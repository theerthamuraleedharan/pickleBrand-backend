package sujus.pickle.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sujus.pickle.common.CurrentUser;
import sujus.pickle.common.IntegerQuantityDeserializer;
import tools.jackson.databind.annotation.JsonDeserialize;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    // Handles customer order creation, history, and retrieval for the authenticated user.
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }

    // Request payload for a standard checkout using an existing saved address.
    public record CheckoutRequest(@NotNull @Positive Long addressId, @Positive Long billingAddressId) { }

    // Request payload for checkout of a single product directly from the product page.
    public record BuyNowRequest(
            @NotNull @Positive Long productId,
            @NotNull @Positive(message = "Quantity must be a positive integer")
            @JsonDeserialize(using = IntegerQuantityDeserializer.class) Integer quantity,
            @NotNull @Positive Long addressId,
            @Positive Long billingAddressId) { }

    // Creates a new order from the user's cart and returns 201 unless the request is replayed.
    @PostMapping
    public ResponseEntity<OrderResponse> create(Authentication authentication,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody CheckoutRequest request) {
        var result = orders.create(CurrentUser.id(authentication), request.addressId(), request.billingAddressId(), key);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .location(URI.create("/api/orders/" + result.order().id()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.order());
    }

    // Creates an order for a single product without requiring the cart to contain that item.
    @PostMapping("/buy-now")
    public ResponseEntity<OrderResponse> buyNow(Authentication authentication,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody BuyNowRequest request) {
        var result = orders.buyNow(CurrentUser.id(authentication), request.productId(), request.quantity(),
                request.addressId(), request.billingAddressId(), key);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .location(URI.create("/api/orders/" + result.order().id()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.order());
    }

    // Returns a paginated history of the signed-in user's orders.
    @GetMapping
    public OrderResponse.History history(Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.history(CurrentUser.id(authentication), page, size);
    }

    // Reads a single order if it belongs to the authenticated user.
    @GetMapping("/{orderId}")
    public OrderResponse get(Authentication authentication, @PathVariable @Positive Long orderId) {
        return orders.get(CurrentUser.id(authentication), orderId);
    }
}
