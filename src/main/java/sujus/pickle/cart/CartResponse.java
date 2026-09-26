package sujus.pickle.cart;

import sujus.pickle.product.ProductResponse;
import java.math.BigDecimal;
import java.util.List;

public record CartResponse(List<Item> items, long itemCount, BigDecimal subtotal, boolean checkoutReady) {
    public record Item(Long productId, ProductResponse product, int quantity,
                       BigDecimal unitPrice, BigDecimal lineTotal, String availability, String message) { }
}
