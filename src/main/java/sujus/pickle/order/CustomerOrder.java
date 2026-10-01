package sujus.pickle.order;

import jakarta.persistence.*;
import sujus.pickle.product.Product;
import sujus.pickle.profile.Address;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.*;

@Entity @Table(name = "customer_orders")
public class CustomerOrder {
    public enum Status { PLACED }
    public enum PaymentStatus { UNPAID }
    public enum PaymentMethod { CASH_ON_DELIVERY }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "user_id", nullable = false)
    private Long userId;
    @Column(name = "idempotency_key", nullable = false)
    private UUID idempotencyKey;
    @Column(name = "address_id", nullable = false)
    private Long addressId;
    @Column(name = "billing_address_id", nullable = false)
    private Long billingAddressId;
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 30)
    private Status status;
    @Enumerated(EnumType.STRING) @Column(name = "payment_status", nullable = false, length = 30)
    private PaymentStatus paymentStatus;
    @Enumerated(EnumType.STRING) @Column(name = "payment_method", nullable = false, length = 30)
    private PaymentMethod paymentMethod;
    @Column(nullable = false, precision = 24, scale = 2)
    private BigDecimal subtotal;
    @Column(name = "delivery_charge", nullable = false, precision = 24, scale = 2)
    private BigDecimal deliveryCharge;
    @Column(nullable = false, precision = 24, scale = 2)
    private BigDecimal tax;
    @Column(nullable = false, precision = 24, scale = 2)
    private BigDecimal total;
    @Column(nullable = false, length = 3)
    private String currency;
    @Embedded
    private DeliveryAddress deliveryAddress;
    @Embedded
    private BillingAddress billingAddress;
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    @OrderBy("id ASC")
    private List<OrderItem> items = new ArrayList<>();

    protected CustomerOrder() { }
    public CustomerOrder(Long userId, UUID idempotencyKey, Address address, Address billingAddress) {
        this.userId = userId;
        this.idempotencyKey = idempotencyKey;
        addressId = address.getId();
        billingAddressId = billingAddress.getId();
        deliveryAddress = new DeliveryAddress(address);
        this.billingAddress = new BillingAddress(billingAddress);
        createdAt = OffsetDateTime.now();
        status = Status.PLACED;
        paymentStatus = PaymentStatus.UNPAID;
        paymentMethod = PaymentMethod.CASH_ON_DELIVERY;
        subtotal = new BigDecimal("0.00");
        deliveryCharge = new BigDecimal("0.00");
        tax = new BigDecimal("0.00");
        total = new BigDecimal("0.00");
        currency = "INR";
    }
    public void addItem(Product product, int quantity) {
        OrderItem item = new OrderItem(this, product, quantity);
        items.add(item);
        subtotal = subtotal.add(item.lineTotal());
        total = subtotal.add(deliveryCharge).add(tax);
    }
    public Long getId() { return id; }
    public Long getAddressId() { return addressId; }
    public Long getBillingAddressId() { return billingAddressId; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
    public Status getStatus() { return status; }
    public PaymentStatus getPaymentStatus() { return paymentStatus; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getDeliveryCharge() { return deliveryCharge; }
    public BigDecimal getTax() { return tax; }
    public BigDecimal getTotal() { return total; }
    public String getCurrency() { return currency; }
    public DeliveryAddress getDeliveryAddress() { return deliveryAddress; }
    public BillingAddress getBillingAddress() { return billingAddress; }
    public List<OrderItem> getItems() { return items; }
}
