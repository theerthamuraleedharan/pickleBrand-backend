package sujus.pickle.cart;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sujus.pickle.product.*;
import sujus.pickle.user.UserRepository;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

@Service
@Transactional
public class CartService {
    private final CartItemRepository items;
    private final CartImportRepository imports;
    private final ProductRepository products;
    private final UserRepository users;

    public CartService(CartItemRepository items, CartImportRepository imports,
                       ProductRepository products, UserRepository users) {
        this.items = items;
        this.imports = imports;
        this.products = products;
        this.users = users;
    }

    // All cart operations and checkout take this same lock, including an empty cart.
    public void lockUser(Long userId) {
        users.findForUpdateById(userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please log in again"));
    }

    public CartResponse get(Long userId) {
        lockUser(userId);
        return response(userId);
    }

    public CartResponse add(Long userId, Long productId, int quantity) {
        lockUser(userId);
        setQuantity(userId, productId, quantity, true);
        return response(userId);
    }

    public CartResponse replace(Long userId, Long productId, int quantity) {
        lockUser(userId);
        if (items.findByUserIdAndProductId(userId, productId).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product is not in your cart");
        }
        setQuantity(userId, productId, quantity, false);
        return response(userId);
    }

    public CartResponse remove(Long userId, Long productId) {
        lockUser(userId);
        items.deleteByUserIdAndProductId(userId, productId);
        return response(userId);
    }

    public CartResponse clear(Long userId) {
        lockUser(userId);
        items.deleteAllByUserId(userId);
        return response(userId);
    }

    public CartResponse importCart(Long userId, CartRequests.ImportCart request) {
        lockUser(userId);
        SortedMap<Long, Integer> quantities = new TreeMap<>();
        for (CartRequests.AddItem item : request.items()) {
            if (quantities.put(item.productId(), item.quantity()) != null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Import contains duplicate product IDs");
            }
        }
        String hash = hash(quantities.toString());
        Optional<CartImport> previous = imports.findByUserIdAndMigrationId(userId, request.migrationId());
        if (previous.isPresent()) {
            if (!previous.get().getRequestHash().equals(hash)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Migration ID was already used with different items");
            }
            return response(userId);
        }
        quantities.forEach((productId, quantity) -> setQuantity(userId, productId, quantity, true));
        imports.save(new CartImport(userId, request.migrationId(), hash));
        return response(userId);
    }

    private void setQuantity(Long userId, Long productId, int quantity, boolean add) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be a positive integer");
        }
        CartItem item = items.findByUserIdAndProductId(userId, productId).orElse(null);
        long desired = (long) quantity + (add && item != null ? item.getQuantity() : 0);
        Product product = products.findForUpdateById(productId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Product " + productId + " was not found; remove it from your cart"));
        validateAvailability(product, desired);
        if (item == null) {
            items.save(new CartItem(userId, productId, (int) desired));
        } else {
            item.setQuantity((int) desired);
        }
    }

    public static void validateAvailability(Product product, long quantity) {
        if (!product.isActive()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Product " + product.getId() + " (" + product.getName() + ") is unavailable; remove it from your cart");
        }
        if (quantity > product.getStockQuantity()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Product " + product.getId() + " (" + product.getName() + ") has only "
                            + product.getStockQuantity() + " available; reduce the quantity or remove it");
        }
    }

    private CartResponse response(Long userId) {
        List<CartResponse.Item> result = new ArrayList<>();
        BigDecimal subtotal = new BigDecimal("0.00");
        long count = 0;
        boolean ready = true;
        for (CartItem item : items.findAllByUserIdOrderByProductIdAsc(userId)) {
            Product product = products.findById(item.getProductId()).orElse(null);
            BigDecimal price = product == null ? null : product.getPrice();
            BigDecimal total = price == null ? null : price.multiply(BigDecimal.valueOf(item.getQuantity()));
            String availability = product == null ? "REMOVED" : !product.isActive() ? "INACTIVE"
                    : item.getQuantity() > product.getStockQuantity() ? "INSUFFICIENT_STOCK" : "AVAILABLE";
            String message = switch (availability) {
                case "REMOVED", "INACTIVE" -> "This product is unavailable; remove it from your cart";
                case "INSUFFICIENT_STOCK" -> "Only " + product.getStockQuantity() + " available; reduce the quantity or remove it";
                default -> null;
            };
            ready &= availability.equals("AVAILABLE");
            if (total != null) subtotal = subtotal.add(total);
            count += item.getQuantity();
            result.add(new CartResponse.Item(item.getProductId(), product == null ? null : ProductResponse.from(product),
                    item.getQuantity(), price, total, availability, message));
        }
        return new CartResponse(result, count, subtotal, ready && !result.isEmpty());
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
