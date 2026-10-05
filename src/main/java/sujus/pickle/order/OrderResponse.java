package sujus.pickle.order;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public record OrderResponse(Long id, OffsetDateTime createdAt, CustomerOrder.Status status,
                            CustomerOrder.PaymentStatus paymentStatus, CustomerOrder.PaymentMethod paymentMethod,
                            DeliveryAddress deliveryAddress, BillingAddress billingAddress,
                            List<Item> items, long itemCount, BigDecimal subtotal, String currency,
                            BigDecimal deliveryCharge, BigDecimal tax, BigDecimal total) {
    public record Item(Long productId, String productName, int weightGrams, BigDecimal unitPrice,
                       int quantity, BigDecimal lineTotal) { }
    public static OrderResponse from(CustomerOrder order) {
        List<Item> items = order.getItems().stream().map(item -> new Item(item.getProductId(),
                item.getProductName(), item.getWeightGrams(), item.getUnitPrice(), item.getQuantity(), item.lineTotal())).toList();
        return new OrderResponse(order.getId(), order.getCreatedAt(), order.getStatus(), order.getPaymentStatus(),
                order.getPaymentMethod(), order.getDeliveryAddress(), order.getBillingAddress(), items,
                items.stream().mapToLong(Item::quantity).sum(), order.getSubtotal(), order.getCurrency(),
                order.getDeliveryCharge(), order.getTax(), order.getTotal());
    }
    public record History(List<OrderResponse> content, int page, int size, long totalElements, int totalPages) { }
}
