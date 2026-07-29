package sujus.pickle.product;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(nullable = false, length = 2000)
    private String description;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal price;

    @Column(name = "stock_quantity", nullable = false)
    private Integer stockQuantity;

    @Column(name = "weight_grams", nullable = false)
    private Integer weightGrams;

    @Enumerated(EnumType.STRING)
    @Column(name = "spice_level", nullable = false, length = 20)
    private SpiceLevel spiceLevel;

    @Column(name = "image_url", length = 500)
    private String imageUrl;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    protected Product() {
    }

    public Product(
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            Integer weightGrams,
            SpiceLevel spiceLevel,
            String imageUrl,
            boolean active
    ) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.weightGrams = weightGrams;
        this.spiceLevel = spiceLevel;
        this.imageUrl = imageUrl;
        this.active = active;
    }

    @PrePersist
    void prePersist() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public void update(
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            Integer weightGrams,
            SpiceLevel spiceLevel,
            String imageUrl,
            boolean active
    ) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.weightGrams = weightGrams;
        this.spiceLevel = spiceLevel;
        this.imageUrl = imageUrl;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStockQuantity() {
        return stockQuantity;
    }

    public Integer getWeightGrams() {
        return weightGrams;
    }

    public SpiceLevel getSpiceLevel() {
        return spiceLevel;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public boolean isActive() {
        return active;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}