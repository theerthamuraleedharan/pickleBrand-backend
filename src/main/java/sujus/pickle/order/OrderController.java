package sujus.pickle.order;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import sujus.pickle.common.CurrentUser;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {
    private final OrderService orders;
    public OrderController(OrderService orders) { this.orders = orders; }
    public record CheckoutRequest(@NotNull @Positive Long addressId) { }

    @PostMapping
    public ResponseEntity<OrderResponse> create(@AuthenticationPrincipal Jwt jwt,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody CheckoutRequest request) {
        var result = orders.create(CurrentUser.id(jwt), request.addressId(), key);
        return ResponseEntity.status(result.replayed() ? 200 : 201)
                .location(URI.create("/api/orders/" + result.order().id()))
                .header("Idempotency-Replayed", Boolean.toString(result.replayed()))
                .body(result.order());
    }

    @GetMapping
    public OrderResponse.History history(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return orders.history(CurrentUser.id(jwt), page, size);
    }

    @GetMapping("/{orderId}")
    public OrderResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable @Positive Long orderId) {
        return orders.get(CurrentUser.id(jwt), orderId);
    }
}
