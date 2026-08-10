package sujus.pickle.admin.product;

import jakarta.validation.constraints.*;
import sujus.pickle.product.ProductCategory;
import sujus.pickle.product.SpiceLevel;

import java.math.BigDecimal;

public record AdminProductRequest(

        @NotBlank(message = "Product name is required")
        @Size(max = 150)
        String name,

        @NotBlank(message = "Description is required")
        @Size(max = 2000)
        String description,

        @NotNull(message = "Price is required")
        @DecimalMin(
                value = "0.01",
                message = "Price must be greater than zero"
        )
        BigDecimal price,

        @NotNull(message = "Stock quantity is required")
        @Min(
                value = 0,
                message = "Stock cannot be negative"
        )
        Integer stockQuantity,

        @NotNull(message = "Weight is required")
        @Min(
                value = 1,
                message = "Weight must be greater than zero"
        )
        Integer weightGrams,

        @NotNull(message = "Spice level is required")
        SpiceLevel spiceLevel,

        @NotNull(message = "Category is required")
        ProductCategory category,

        Boolean active
) {
}