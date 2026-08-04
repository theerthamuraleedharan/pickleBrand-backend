package sujus.pickle.product;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record ProductRequest(

        @NotBlank
        @Size(max = 150)
        String name,

        @NotBlank
        @Size(max = 2000)
        String description,

        @NotNull
        @DecimalMin("0.01")
        BigDecimal price,

        @NotNull
        @Min(0)
        Integer stockQuantity,

        @NotNull
        @Min(1)
        Integer weightGrams,

        @NotNull
        SpiceLevel spiceLevel,

        @Size(max = 500)
        String imageUrl,

        Boolean active,

        @NotNull(message = "Product category is required")
        ProductCategory category
) {
}