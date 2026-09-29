package sujus.pickle.security.oidc;

import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import sujus.pickle.PostgresIntegrationTest;
import sujus.pickle.cart.CartService;
import sujus.pickle.order.OrderService;
import sujus.pickle.product.*;
import sujus.pickle.profile.*;
import sujus.pickle.user.*;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real RSA signatures + a local JWKS endpoint + PostgreSQL; no mocked authentication. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("oidc")
class OidcIntegrationTests extends PostgresIntegrationTest {
    private static final RSAKey FIRST_KEY = rsa("first");
    private static volatile RSAKey publishedKey = FIRST_KEY;
    private static final HttpServer KEYS = startKeys();
    private static final String ISSUER = "http://localhost:" + KEYS.getAddress().getPort() + "/realm";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired UserRepository users;
    @Autowired ExternalIdentityRepository identities;
    @Autowired ProductRepository products;
    @Autowired AddressRepository addresses;
    @Autowired CartService carts;
    @Autowired OrderService orders;

    @DynamicPropertySource
    static void provider(DynamicPropertyRegistry properties) {
        properties.add("app.oidc.issuer-uri", () -> ISSUER);
        properties.add("app.oidc.jwk-set-uri", () -> ISSUER + "/certs");
        properties.add("app.oidc.audience", () -> "pickle-api");
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE users, products RESTART IDENTITY CASCADE");
        publishedKey = FIRST_KEY;
    }

    @AfterAll
    static void closeKeys() { KEYS.stop(0); }

    @Test
    void provisionsOnceAndUsesLocalIdentityAcrossProfileAndCart() throws Exception {
        String token = token(Map.of("userId", 999999L));
        long id = localId(token);
        assertThat(localId(token)).isEqualTo(id);
        assertThat(users.count()).isEqualTo(1);
        assertThat(identities.count()).isEqualTo(1);
        assertThat(users.findById(id).orElseThrow().getPasswordHash()).isNull();
        mvc.perform(get("/api/profile").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id));
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void rejectsWrongIssuerAudienceExpiryTypeAndMissingClaims() throws Exception {
        for (Map<String, Object> overrides : List.<Map<String, Object>>of(
                Map.of("iss", "https://untrusted.example/realm"), Map.of("aud", List.of("other-api")),
                Map.of("exp", Date.from(Instant.now().minusSeconds(300))), Map.of("typ", "ID"),
                Map.of("sub", ""), Map.of("nbf", Date.from(Instant.now().plusSeconds(300))))) {
            mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token(overrides)))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.status").value(401));
        }
        Map<String, Object> noExpiry = new HashMap<>();
        noExpiry.put("exp", null);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token(noExpiry)))
                .andExpect(status().isUnauthorized());
        Map<String, Object> noAudience = new HashMap<>();
        noAudience.put("aud", null);
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token(noAudience)))
                .andExpect(status().isUnauthorized());
        assertThat(users.count()).isZero();
    }

    @Test
    void rejectsForgedSignatureAndUnsignedToken() throws Exception {
        String forged = sign(Map.of(), rsa("forged"));
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + forged)).andExpect(status().isUnauthorized());
        String unsigned = new PlainJWT(claims(Map.of())).serialize();
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + unsigned)).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized());
        assertThat(users.count()).isZero();
    }

    @Test
    void onlyApiClientAdminRoleGrantsAdminAccess() throws Exception {
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token(Map.of(
                        "role", "ADMIN", "realm_access", Map.of("roles", List.of("ADMIN"))))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.status").value(403));
        String admin = token(Map.of("resource_access", Map.of("pickle-api", Map.of("roles", List.of("ADMIN")))));
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        mvc.perform(get("/api/profile").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("ADMIN"));
        // OIDC permissions do not leak into legacy password-login permissions.
        assertThat(users.findById(localId(admin)).orElseThrow().getRole()).isEqualTo(Role.CUSTOMER);
    }

    @Test
    void ignoresRolesForOtherClientsAndRequiresAnApplicationRole() throws Exception {
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + token(Map.of(
                        "resource_access", Map.of("other-api", Map.of("roles", List.of("ADMIN")))))))
                .andExpect(status().isUnauthorized());
        assertThat(users.count()).isZero();
    }

    @Test
    void doesNotLinkAnExistingAccountByEmail() throws Exception {
        AppUser existing = users.save(new AppUser("Local", "Admin", "alice@example.test", "legacy-hash", Role.ADMIN));
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token(Map.of())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("account linking")));
        assertThat(identities.count()).isZero();
        assertThat(users.count()).isEqualTo(1);

        // This represents the explicit operator-reviewed link, never an email auto-merge.
        identities.save(new ExternalIdentity(existing.getId(), ISSUER, "alice-subject"));
        assertThat(localId(token(Map.of()))).isEqualTo(existing.getId());
        mvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + token(Map.of())))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/profile").header("Authorization", "Bearer " + token(Map.of())))
                .andExpect(status().isOk()).andExpect(jsonPath("$.role").value("CUSTOMER"));
    }

    @Test
    void linkingPreservesCartAddressAndOrderOwnership() throws Exception {
        AppUser existing = users.save(new AppUser("Local", "Customer", "alice@example.test", "hash", Role.CUSTOMER));
        Product product = products.save(new Product("Mango", "Pickle", new BigDecimal("100.00"),
                10, 250, SpiceLevel.MILD, ProductCategory.VEG, true));
        Address address = addresses.save(new Address(existing, "Alice", "9876543210", "Road 1", null,
                "Kochi", "Kerala", "682001", "India", true));
        carts.add(existing.getId(), product.getId(), 1);
        long orderId = orders.create(existing.getId(), address.getId(), UUID.randomUUID()).order().id();
        carts.add(existing.getId(), product.getId(), 2);
        identities.save(new ExternalIdentity(existing.getId(), ISSUER, "alice-subject"));
        String alice = token(Map.of());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemCount").value(2));
        mvc.perform(get("/api/profile/addresses").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(address.getId()));
        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", "Bearer " + alice)).andExpect(status().isOk());
        String bob = token(Map.of("sub", "bob-subject", "email", "bob@example.test", "userId", existing.getId()));
        mvc.perform(get("/api/orders/{id}", orderId).header("Authorization", "Bearer " + bob)).andExpect(status().isNotFound());
        mvc.perform(get("/api/cart").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.itemCount").value(0));
    }

    @Test
    void subjectRemainsStableWhenContactEmailChanges() throws Exception {
        long id = localId(token(Map.of()));
        assertThat(localId(token(Map.of("email", "new-email@example.test")))).isEqualTo(id);
        assertThat(users.count()).isEqualTo(1);
    }

    @Test
    void newAccountsRequireVerifiedEmail() throws Exception {
        mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token(Map.of("email_verified", false))))
                .andExpect(status().isUnauthorized());
        assertThat(users.count()).isZero();
    }

    @Test
    void concurrentFirstRequestsCreateOneIdentity() throws Exception {
        String token = token(Map.of());
        ExecutorService pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<Long> request = () -> { start.await(10, TimeUnit.SECONDS); return localId(token); };
            Future<Long> first = pool.submit(request);
            Future<Long> second = pool.submit(request);
            start.countDown();
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
            assertThat(users.count()).isEqualTo(1);
            assertThat(identities.count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void refreshesPublicKeysWhenProviderRotatesSigningKey() throws Exception {
        long id = localId(token(Map.of()));
        publishedKey = rsa("rotated-" + UUID.randomUUID());
        assertThat(localId(sign(Map.of(), publishedKey))).isEqualTo(id);
    }

    @Test
    void localCustomerRegistrationAndLoginWorkAlongsideOidcAndCors() throws Exception {
        String registration = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"Local","lastName":"Customer","email":"local@example.test","password":"StrongPassword!123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.user.role").value("CUSTOMER"))
                .andReturn().getResponse().getContentAsString();
        String localAccessToken = mapper.readTree(registration).get("accessToken").asText();
        mvc.perform(get("/api/profile").header("Authorization", "Bearer " + localAccessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("local@example.test"));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"local@example.test","password":"StrongPassword!123"}
                                """))
                .andExpect(status().isOk()).andExpect(jsonPath("$.user.role").value("CUSTOMER"));

        String oidcAccessToken = token(Map.of("sub", "keycloak-customer", "email", "oidc@example.test"));
        mvc.perform(get("/api/profile").header("Authorization", "Bearer " + oidcAccessToken))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("oidc@example.test"));

        mvc.perform(options("/api/cart").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET")
                        .header("Access-Control-Request-Headers", "Authorization"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));
        mvc.perform(options("/api/auth/login").header("Origin", "http://localhost:5174")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5174"));
        mvc.perform(get("/api/products")).andExpect(status().isOk());
    }

    private long localId(String token) throws Exception {
        String body = mvc.perform(get("/api/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).get("id").asLong();
    }
    private static String token(Map<String, Object> overrides) throws Exception { return sign(overrides, FIRST_KEY); }
    private static String sign(Map<String, Object> overrides, RSAKey key) throws Exception {
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(key.getKeyID()).build(), claims(overrides));
        jwt.sign(new RSASSASigner(key));
        return jwt.serialize();
    }
    private static JWTClaimsSet claims(Map<String, Object> overrides) {
        JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder().issuer(ISSUER).subject("alice-subject")
                .audience("pickle-api").issueTime(new Date()).expirationTime(Date.from(Instant.now().plusSeconds(300)))
                .claim("typ", "Bearer").claim("email", "alice@example.test").claim("email_verified", true)
                .claim("given_name", "Alice").claim("family_name", "Example")
                .claim("resource_access", Map.of("pickle-api", Map.of("roles", List.of("CUSTOMER"))));
        overrides.forEach(claims::claim);
        return claims.build();
    }
    private static RSAKey rsa(String id) {
        try { return new RSAKeyGenerator(2048).keyID(id).generate(); }
        catch (JOSEException exception) { throw new IllegalStateException(exception); }
    }
    private static HttpServer startKeys() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/realm/certs", exchange -> {
                byte[] json = new JWKSet(publishedKey.toPublicJWK()).toString().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, json.length);
                try (var output = exchange.getResponseBody()) { output.write(json); }
            });
            server.start();
            return server;
        } catch (java.io.IOException exception) { throw new IllegalStateException(exception); }
    }
}
