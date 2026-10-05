package sujus.pickle.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import sujus.pickle.common.CurrentUser;

@RestController
@RequestMapping("/api/cart")
public class CartController {
    private final CartService carts;
    public CartController(CartService carts) { this.carts = carts; }

    // Returns the authenticated user's current cart and availability summary.
    @GetMapping
    public CartResponse get(Authentication authentication) {
        return carts.get(CurrentUser.id(authentication));
    }

    // Adds a product to the cart or increases its quantity while enforcing inventory checks.
    @PostMapping("/items")
    public CartResponse add(Authentication authentication, @Valid @RequestBody CartRequests.AddItem request) {
        return carts.add(CurrentUser.id(authentication), request.productId(), request.quantity());
    }

    // Updates the quantity for an existing cart item using a replacement strategy.
    @PatchMapping("/items/{productId}")
    public CartResponse replace(Authentication authentication, @Positive @PathVariable Long productId,
                                @Valid @RequestBody CartRequests.UpdateItem request) {
        return carts.replace(CurrentUser.id(authentication), productId, request.quantity());
    }

    // Removes one product from the authenticated user's cart.
    @DeleteMapping("/items/{productId}")
    public CartResponse remove(Authentication authentication, @Positive @PathVariable Long productId) {
        return carts.remove(CurrentUser.id(authentication), productId);
    }

    // Clears the full cart for the signed-in user.
    @DeleteMapping
    public CartResponse clear(Authentication authentication) {
        return carts.clear(CurrentUser.id(authentication));
    }

    // Imports a saved cart snapshot for migration or restore flows.
    @PostMapping("/import")
    public CartResponse importCart(Authentication authentication, @Valid @RequestBody CartRequests.ImportCart request) {
        return carts.importCart(CurrentUser.id(authentication), request);
    }
}
