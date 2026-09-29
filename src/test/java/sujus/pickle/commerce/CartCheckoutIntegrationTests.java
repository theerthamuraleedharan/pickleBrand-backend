package sujus.pickle.commerce;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import sujus.pickle.PostgresIntegrationTest;
import sujus.pickle.cart.*;
import sujus.pickle.order.*;
import sujus.pickle.product.*;
import sujus.pickle.profile.*;
import sujus.pickle.security.JwtService;
import sujus.pickle.user.*;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@org.springframework.test.context.ActiveProfiles("local")
class CartCheckoutIntegrationTests extends PostgresIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired CartService carts;
    @Autowired OrderService orders;
    @Autowired UserRepository users;
    @Autowired ProductRepository products;
    @Autowired AddressRepository addresses;
    @Autowired JwtService jwt;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;
    private AppUser alice;
    private AppUser bob;
    private Address aliceAddress;
    private Address bobAddress;
    private Product product;

    @BeforeEach
    void fixtures() {
        jdbc.execute("TRUNCATE users, products RESTART IDENTITY CASCADE");
        // Product references intentionally survive deletion, so clear their snapshots explicitly.
        jdbc.execute("TRUNCATE customer_orders, cart_items, cart_imports RESTART IDENTITY CASCADE");
        alice = users.save(new AppUser("Alice", "Test", "alice@example.com", "unused", Role.CUSTOMER));
        bob = users.save(new AppUser("Bob", "Test", "bob@example.com", "unused", Role.CUSTOMER));
        aliceAddress = addresses.save(address(alice, "Kerala"));
        bobAddress = addresses.save(address(bob, "Kerala"));
        product = products.save(new Product("Mango pickle", "Fresh mango pickle", new BigDecimal("120.25"),
                10, 250, SpiceLevel.MEDIUM, ProductCategory.VEG, true));
    }

    private Address address(AppUser user, String state) {
        return new Address(user, "Test Customer", "9876543210", "10 Market Road", null,
                "Kochi", state, "682001", "India", false);
    }
    private String bearer(AppUser user) { return "Bearer " + jwt.generateAccessToken(user); }
    private String item(int quantity) { return "{\"productId\":" + product.getId() + ",\"quantity\":" + quantity + "}"; }

    @Test
    void authenticationAndOwnershipAreEnforced() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        mvc.perform(get("/api/orders").header("Authorization", "Bearer invalid"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.message").isNotEmpty());
        carts.add(alice.getId(), product.getId(), 2);
        mvc.perform(get("/api/cart").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemCount").value(0));
        mvc.perform(patch("/api/cart/items/{id}", product.getId()).header("Authorization", bearer(bob))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":1}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/cart/items/{id}", product.getId()).header("Authorization", bearer(bob)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/cart").header("Authorization", bearer(bob))).andExpect(status().isOk());
        assertThat(carts.get(alice.getId()).itemCount()).isEqualTo(2);
        assertThatThrownBy(() -> orders.create(alice.getId(), bobAddress.getId(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("404");
        var order = orders.create(alice.getId(), aliceAddress.getId(), UUID.randomUUID()).order();
        mvc.perform(get("/api/orders/{id}", order.id()).header("Authorization", bearer(bob)))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/orders").header("Authorization", bearer(bob)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get("/api/orders/{id}", order.id()).header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.currency").value("INR"));
    }

    @Test
    void cartValidatesStrictQuantitiesAndStock() throws Exception {
        for (String value : List.of("0", "-1", "1.5", "1.0", "null", "\"2\"", "2147483648")) {
            mvc.perform(post("/api/cart/items").header("Authorization", bearer(alice))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"productId\":" + product.getId() + ",\"quantity\":" + value + "}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.path").value("/api/cart/items"));
        }
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(item(11)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("only 10")));
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"productId\":99999,\"quantity\":1}"))
                .andExpect(status().isNotFound());
        jdbc.update("UPDATE products SET active=false WHERE id=?", product.getId());
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(item(1)))
                .andExpect(status().isConflict());
    }

    @Test
    void cartUsesCurrentPricesAndDoesNotReserveStock() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content(item(2)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(240.50));
        carts.add(alice.getId(), product.getId(), 1);
        assertThat(carts.get(alice.getId()).items()).hasSize(1);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        jdbc.update("UPDATE products SET price=125.35 WHERE id=?", product.getId());
        mvc.perform(patch("/api/cart/items/{id}", product.getId()).header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":2}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(250.70))
                .andExpect(jsonPath("$.items[0].unitPrice").value(125.35));
        assertThatThrownBy(() -> carts.add(alice.getId(), product.getId(), Integer.MAX_VALUE))
                .isInstanceOf(ResponseStatusException.class);
        mvc.perform(delete("/api/cart/items/{id}", product.getId()).header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        carts.add(alice.getId(), product.getId(), 1);
        mvc.perform(delete("/api/cart").header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.subtotal").value(0));
    }

    @Test
    void checkoutSnapshotsAndHistorySurviveProductAndAddressChanges() throws Exception {
        carts.add(alice.getId(), product.getId(), 3);
        jdbc.update("UPDATE products SET price=130.45 WHERE id=?", product.getId());
        var result = orders.create(alice.getId(), aliceAddress.getId(), UUID.randomUUID());
        assertThat(result.order().subtotal()).isEqualByComparingTo("391.35");
        assertThat(result.order().status()).isEqualTo(CustomerOrder.Status.AWAITING_QUOTE);
        assertThat(result.order().paymentStatus()).isEqualTo(CustomerOrder.PaymentStatus.UNPAID);
        assertThat(result.order().total()).isNull();
        assertThat(result.order().deliveryCharge()).isNull();
        assertThat(result.order().tax()).isNull();
        assertThat(carts.get(alice.getId()).items()).isEmpty();
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(7);
        products.deleteById(product.getId());
        addresses.deleteById(aliceAddress.getId());
        var saved = orders.get(alice.getId(), result.order().id());
        assertThat(saved.items().get(0).productName()).isEqualTo("Mango pickle");
        assertThat(saved.items().get(0).weightGrams()).isEqualTo(250);
        assertThat(saved.deliveryAddress().getCity()).isEqualTo("Kochi");
        mvc.perform(get("/api/orders?page=0&size=1").header("Authorization", bearer(alice)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1));
        mvc.perform(get("/api/orders?size=101").header("Authorization", bearer(alice))).andExpect(status().isBadRequest());
    }

    @Test
    void rejectsEmptyCartAndAddressesOutsideKerala() {
        assertThatThrownBy(() -> orders.create(alice.getId(), aliceAddress.getId(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("cart is empty");
        carts.add(alice.getId(), product.getId(), 1);
        Address outside = addresses.save(address(alice, "Tamil Nadu"));
        assertThatThrownBy(() -> orders.create(alice.getId(), outside.getId(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("Kerala");
        jdbc.update("UPDATE addresses SET phone='' WHERE id=?", aliceAddress.getId());
        assertThatThrownBy(() -> orders.create(alice.getId(), aliceAddress.getId(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("incomplete");
        assertThat(carts.get(alice.getId()).itemCount()).isEqualTo(1);
    }

    @Test
    void unavailableCartItemsRemainRemovable() {
        carts.add(alice.getId(), product.getId(), 3);
        jdbc.update("UPDATE products SET stock_quantity=1 WHERE id=?", product.getId());
        assertThat(carts.get(alice.getId()).items().get(0).availability()).isEqualTo("INSUFFICIENT_STOCK");
        jdbc.update("UPDATE products SET active=false WHERE id=?", product.getId());
        assertThatThrownBy(() -> orders.create(alice.getId(), aliceAddress.getId(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("unavailable");
        products.deleteById(product.getId());
        assertThat(carts.get(alice.getId()).items().get(0).availability()).isEqualTo("REMOVED");
        assertThat(carts.get(alice.getId()).checkoutReady()).isFalse();
        assertThatThrownBy(() -> orders.create(alice.getId(), aliceAddress.getId(), UUID.randomUUID()))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("no longer available");
        assertThat(carts.remove(alice.getId(), product.getId()).items()).isEmpty();
    }

    @Test
    void laterStockConflictRollsBackEarlierDeduction() {
        Product second = products.save(new Product("Lemon", "Lemon pickle", new BigDecimal("99.00"),
                3, 200, SpiceLevel.MILD, ProductCategory.VEG, true));
        carts.add(alice.getId(), product.getId(), 2);
        carts.add(alice.getId(), second.getId(), 2);
        jdbc.update("UPDATE products SET stock_quantity=0 WHERE id=?", second.getId());
        UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> orders.create(alice.getId(), aliceAddress.getId(), key))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("only 0");
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
        assertThat(carts.get(alice.getId()).itemCount()).isEqualTo(4);
        assertThat(orders.history(alice.getId(), 0, 20).totalElements()).isZero();
        jdbc.update("UPDATE products SET stock_quantity=2 WHERE id=?", second.getId());
        assertThat(orders.create(alice.getId(), aliceAddress.getId(), key).replayed()).isFalse();
    }

    @Test
    void databaseFailureDuringCartClearRollsBackOrderAndStock() {
        carts.add(alice.getId(), product.getId(), 2);
        jdbc.execute("CREATE FUNCTION reject_cart_delete() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'test rollback'; END $$");
        jdbc.execute("CREATE TRIGGER reject_cart_delete BEFORE DELETE ON cart_items FOR EACH ROW EXECUTE FUNCTION reject_cart_delete()");
        try {
            assertThatThrownBy(() -> orders.create(alice.getId(), aliceAddress.getId(), UUID.randomUUID()))
                    .isInstanceOf(RuntimeException.class);
            assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(10);
            assertThat(carts.get(alice.getId()).itemCount()).isEqualTo(2);
            assertThat(orders.history(alice.getId(), 0, 20).totalElements()).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER reject_cart_delete ON cart_items");
            jdbc.execute("DROP FUNCTION reject_cart_delete()");
        }
    }

    @Test
    void idempotentHttpRetriesReturnOriginalOrderAndRejectChangedAddress() throws Exception {
        carts.add(alice.getId(), product.getId(), 2);
        String key = UUID.randomUUID().toString();
        String body = "{\"addressId\":" + aliceAddress.getId() + "}";
        mvc.perform(post("/api/orders").header("Authorization", bearer(alice)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated()).andExpect(header().string("Idempotency-Replayed", "false"));
        // A later cart must not be consumed by a retry of the earlier submission.
        carts.add(alice.getId(), product.getId(), 1);
        mvc.perform(post("/api/orders").header("Authorization", bearer(alice)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andExpect(header().string("Idempotency-Replayed", "true"));
        assertThat(carts.get(alice.getId()).itemCount()).isEqualTo(1);
        assertThat(orders.history(alice.getId(), 0, 20).totalElements()).isEqualTo(1);
        mvc.perform(post("/api/orders").header("Authorization", bearer(alice)).header("Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"addressId\":" + bobAddress.getId() + "}"))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/orders").header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value(400));
        mvc.perform(post("/api/orders").header("Authorization", bearer(alice)).header("Idempotency-Key", "bad")
                        .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
    }

    @Test
    void concurrentBuyersCannotOversell() throws Exception {
        jdbc.update("UPDATE products SET stock_quantity=1 WHERE id=?", product.getId());
        carts.add(alice.getId(), product.getId(), 1);
        carts.add(bob.getId(), product.getId(), 1);
        List<Boolean> results = race(
                () -> attempt(alice.getId(), aliceAddress.getId()),
                () -> attempt(bob.getId(), bobAddress.getId()));
        assertThat(results).containsExactlyInAnyOrder(true, false);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM customer_orders", Long.class)).isEqualTo(1);
        assertThat(carts.get(alice.getId()).itemCount() + carts.get(bob.getId()).itemCount()).isEqualTo(1);
    }

    private boolean attempt(Long user, Long address) {
        try { orders.create(user, address, UUID.randomUUID()); return true; }
        catch (ResponseStatusException exception) {
            assertThat(exception.getStatusCode().value()).isEqualTo(409);
            return false;
        }
    }

    @Test
    void clientCannotOverrideOwnershipOrOrderAmounts() throws Exception {
        mvc.perform(post("/api/cart/items").header("Authorization", bearer(alice))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":" + product.getId() + ",\"quantity\":2,\"userId\":" + bob.getId() + "}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemCount").value(2));
        assertThat(carts.get(bob.getId()).itemCount()).isZero();
        mvc.perform(post("/api/orders").header("Authorization", bearer(alice))
                        .header("Idempotency-Key", UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"addressId\":" + aliceAddress.getId()
                                + ",\"total\":0.01,\"subtotal\":0.01,\"paymentStatus\":\"PAID\",\"userId\":" + bob.getId() + "}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.subtotal").value(240.50))
                .andExpect(jsonPath("$.paymentStatus").value("UNPAID"));
        assertThat(orders.history(bob.getId(), 0, 20).totalElements()).isZero();
        // Existing public catalog routes remain accessible without a token.
        mvc.perform(get("/api/products")).andExpect(status().isOk());
    }

    @Test
    void idempotencyKeysAreScopedToTheAuthenticatedUser() {
        UUID key = UUID.randomUUID();
        carts.add(alice.getId(), product.getId(), 1);
        carts.add(bob.getId(), product.getId(), 1);
        var first = orders.create(alice.getId(), aliceAddress.getId(), key);
        var second = orders.create(bob.getId(), bobAddress.getId(), key);
        assertThat(first.order().id()).isNotEqualTo(second.order().id());
        assertThat(second.replayed()).isFalse();
    }

    @Test
    void simultaneousRetriesAndCartAddsAreSerialized() throws Exception {
        race(() -> carts.add(alice.getId(), product.getId(), 1), () -> carts.add(alice.getId(), product.getId(), 1));
        assertThat(carts.get(alice.getId()).itemCount()).isEqualTo(2);
        assertThat(carts.get(alice.getId()).items()).hasSize(1);
        UUID key = UUID.randomUUID();
        var results = race(() -> orders.create(alice.getId(), aliceAddress.getId(), key),
                () -> orders.create(alice.getId(), aliceAddress.getId(), key));
        assertThat(results.get(0).order().id()).isEqualTo(results.get(1).order().id());
        assertThat(results).extracting(OrderService.Submission::replayed).containsExactlyInAnyOrder(true, false);
        assertThat(products.findById(product.getId()).orElseThrow().getStockQuantity()).isEqualTo(8);
    }

    @Test
    void checkoutRechecksStockAfterConcurrentWriterCommits() throws Exception {
        carts.add(alice.getId(), product.getId(), 2);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch locked = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try {
            Future<?> writer = executor.submit(() -> new TransactionTemplate(transactions).executeWithoutResult(tx -> {
                products.findForUpdateById(product.getId()).orElseThrow();
                locked.countDown();
                await(release);
                jdbc.update("UPDATE products SET stock_quantity=0 WHERE id=?", product.getId());
            }));
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            Future<Boolean> checkout = executor.submit(() -> attempt(alice.getId(), aliceAddress.getId()));
            release.countDown();
            writer.get(15, TimeUnit.SECONDS);
            assertThat(checkout.get(15, TimeUnit.SECONDS)).isFalse();
            assertThat(carts.get(alice.getId()).itemCount()).isEqualTo(2);
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void localImportIsAtomicAndIdempotentEvenAfterCheckout() throws Exception {
        UUID migration = UUID.randomUUID();
        var request = new CartRequests.ImportCart(migration, List.of(new CartRequests.AddItem(product.getId(), 2)));
        carts.add(alice.getId(), product.getId(), 1);
        race(() -> carts.importCart(alice.getId(), request), () -> carts.importCart(alice.getId(), request));
        assertThat(carts.get(alice.getId()).itemCount()).isEqualTo(3);
        assertThatThrownBy(() -> carts.importCart(alice.getId(), new CartRequests.ImportCart(migration,
                List.of(new CartRequests.AddItem(product.getId(), 3)))))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("different items");
        orders.create(alice.getId(), aliceAddress.getId(), UUID.randomUUID());
        assertThat(carts.importCart(alice.getId(), request).items()).isEmpty();
        assertThatThrownBy(() -> carts.importCart(alice.getId(), new CartRequests.ImportCart(UUID.randomUUID(),
                List.of(new CartRequests.AddItem(product.getId(), 1), new CartRequests.AddItem(99999L, 1)))))
                .isInstanceOf(ResponseStatusException.class);
        assertThat(carts.get(alice.getId()).items()).isEmpty();
        mvc.perform(post("/api/cart/import").header("Authorization", bearer(alice)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"migrationId\":\"" + UUID.randomUUID() + "\",\"items\":[" + item(1) + "]}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemCount").value(1));
    }

    private <T> List<T> race(Callable<T> first, Callable<T> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<T> a = executor.submit(() -> { await(start); return first.call(); });
            Future<T> b = executor.submit(() -> { await(start); return second.call(); });
            start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally { executor.shutdownNow(); }
    }
    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Timed out waiting for concurrent transaction");
        } catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
}
