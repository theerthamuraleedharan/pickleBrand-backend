package sujus.pickle.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import sujus.pickle.common.CurrentUser;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService carts;
    public CartController(CartService carts) { this.carts = carts; }

    @GetMapping
    public CartResponse get(@AuthenticationPrincipal Jwt jwt) {
        return carts.get(CurrentUser.id(jwt));
    }

    @PostMapping("/items")
    public CartResponse add(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartRequests.AddItem request) {
        return carts.add(CurrentUser.id(jwt), request.productId(), request.quantity());
    }

    @PatchMapping("/items/{productId}")
    public CartResponse replace(@AuthenticationPrincipal Jwt jwt, @Positive @PathVariable Long productId,
                                @Valid @RequestBody CartRequests.UpdateItem request) {
        return carts.replace(CurrentUser.id(jwt), productId, request.quantity());
    }

    @DeleteMapping("/items/{productId}")
    public CartResponse remove(@AuthenticationPrincipal Jwt jwt, @Positive @PathVariable Long productId) {
        return carts.remove(CurrentUser.id(jwt), productId);
    }

    @DeleteMapping
    public CartResponse clear(@AuthenticationPrincipal Jwt jwt) {
        return carts.clear(CurrentUser.id(jwt));
    }

    @PostMapping("/import")
    public CartResponse importCart(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CartRequests.ImportCart request) {
        return carts.importCart(CurrentUser.id(jwt), request);
    }
}
