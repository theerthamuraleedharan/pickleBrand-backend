package sujus.pickle.cart;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import tools.jackson.databind.annotation.JsonDeserialize;
import sujus.pickle.common.IntegerQuantityDeserializer;
import java.util.List;
import java.util.UUID;

public final class CartRequests {
    private CartRequests() { }
    public record AddItem(
            @NotNull @Positive Long productId,
            @NotNull @Positive(message = "Quantity must be a positive integer")
            @JsonDeserialize(using = IntegerQuantityDeserializer.class) Integer quantity) { }
    public record UpdateItem(
            @NotNull @Positive(message = "Quantity must be a positive integer")
            @JsonDeserialize(using = IntegerQuantityDeserializer.class) Integer quantity) { }
    public record ImportCart(
            @NotNull UUID migrationId,
            @NotNull @Size(min = 1, max = 100) List<@NotNull @Valid AddItem> items) { }
}
