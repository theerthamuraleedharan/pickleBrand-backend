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

    @Column(name = "image_name", length = 255)
    private String imageName;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ProductCategory category;

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
            boolean active,
            ProductCategory category
    ) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.weightGrams = weightGrams;
        this.spiceLevel = spiceLevel;
        this.imageUrl = imageUrl;
        this.active = active;
        this.category = category;
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

    public Product(
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            Integer weightGrams,
            SpiceLevel spiceLevel,
            ProductCategory category,
            boolean active
    ) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.weightGrams = weightGrams;
        this.spiceLevel = spiceLevel;
        this.category = category;
        this.active = active;
    }

    public void update(
            String name,
            String description,
            BigDecimal price,
            Integer stockQuantity,
            Integer weightGrams,
            SpiceLevel spiceLevel,
            ProductCategory category,
            boolean active
    ) {
        this.name = name;
        this.description = description;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.weightGrams = weightGrams;
        this.spiceLevel = spiceLevel;
        this.category = category;
        this.active = active;
    }

    public Long getId() {
        return id;
    }

    public void deductStock(int quantity) {
        if (quantity <= 0 || quantity > stockQuantity) {
            throw new IllegalArgumentException("Invalid stock deduction");
        }
        stockQuantity -= quantity;
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

    public ProductCategory getCategory() {
        return category;
    }

    public void setCategory(ProductCategory category) {
        this.category = category;
    }

    public String getImageName() {
        return imageName;
    }

    public void setImageName(String imageName) {
        this.imageName = imageName;
    }

    public void updateImageName(String imageName) {
        this.imageName = imageName;
    }

    public void removeImage() {
        this.imageName = null;
    }
}
