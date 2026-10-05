package sujus.pickle.product;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        Integer weightGrams,
        SpiceLevel spiceLevel,
        String imageUrl,
        boolean active,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,

        ProductCategory category
) {

    // Converts the entity into the public API shape used by the storefront and admin screens.
    public static ProductResponse from(Product product) {

        String imageUrl =
                product.getImageName() == null
                        ? null
                        : "/api/products/"
                        + product.getId()
                        + "/photo";

        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getWeightGrams(),
                product.getSpiceLevel(),
                imageUrl,
                product.isActive(),
                product.getCreatedAt(),
                product.getUpdatedAt(),
                product.getCategory()
        );
    }
}