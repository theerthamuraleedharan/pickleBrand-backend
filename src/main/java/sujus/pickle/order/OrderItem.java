package sujus.pickle.order;

import jakarta.persistence.*;
import sujus.pickle.product.Product;
import java.math.BigDecimal;

@Entity @Table(name = "order_items")
public class OrderItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private CustomerOrder order;
    @Column(name = "product_id", nullable = false)
    private Long productId;
    @Column(name = "product_name", nullable = false, length = 150)
    private String productName;
    @Column(name = "weight_grams", nullable = false)
    private int weightGrams;
    @Column(name = "unit_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal unitPrice;
    @Column(nullable = false)
    private int quantity;
    protected OrderItem() { }
    public OrderItem(CustomerOrder order, Product product, int quantity) {
        this.order = order;
        productId = product.getId();
        productName = product.getName();
        weightGrams = product.getWeightGrams();
        unitPrice = product.getPrice();
        this.quantity = quantity;
    }
    public Long getProductId() { return productId; }
    public String getProductName() { return productName; }
    public int getWeightGrams() { return weightGrams; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public int getQuantity() { return quantity; }
    public BigDecimal lineTotal() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
