package sujus.pickle.order;

import jakarta.persistence.*;
import sujus.pickle.product.Product;
import sujus.pickle.profile.Address;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name = "customer_orders")
public class CustomerOrder {
    public enum Status { AWAITING_QUOTE }
    public enum PaymentStatus { UNPAID }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;
    @Column(name = "address_id", nullable = false)
    private Long addressId;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Status status;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus;
    @Column(nullable = false, precision = 24, scale = 2)
    private BigDecimal subtotal;
    @Column(nullable = false, length = 3)
    private String currency;
    @Embedded
    private DeliveryAddress deliveryAddress;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() { }
    public CustomerOrder(Long userId, UUID idempotencyKey, Address address) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        addressId = address.getId();
        deliveryAddress = new DeliveryAddress(address);
        createdAt = OffsetDateTime.now();
        status = Status.AWAITING_QUOTE;
        paymentStatus = PaymentStatus.UNPAID;
        subtotal = new BigDecimal("0.00");
        currency = "INR";
    }
    public void addItem(Product product, int quantity) {
        OrderItem item = new OrderItem(this, product, quantity);
        items.add(item);
        subtotal = subtotal.add(item.lineTotal());
    }
    public Long getId() { return id; }
    public Long getAddressId() { return addressId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public BigDecimal getSubtotal() { return subtotal; }
    public String getCurrency() { return currency; }
    public DeliveryAddress getDeliveryAddress() { return deliveryAddress; }
    public List<OrderItem> getItems() { return items; }
}
