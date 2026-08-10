package sujus.pickle.admin.product;

import sujus.pickle.product.*;

import java.math.BigDecimal;

public record AdminProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        Integer weightGrams,
        SpiceLevel spiceLevel,
        ProductCategory category,
        boolean active,
        String imageUrl
) {

    public static AdminProductResponse from(
            Product product
    ) {
        String imageUrl =
                product.getImageName() == null
                        ? null
                        : "/api/products/"
                        + product.getId()
                        + "/photo";

        return new AdminProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getWeightGrams(),
                product.getSpiceLevel(),
                product.getCategory(),
                product.isActive(),
                imageUrl
        );
    }
}