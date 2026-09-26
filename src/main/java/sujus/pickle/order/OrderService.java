package sujus.pickle.order;

import jakarta.validation.Validator;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import sujus.pickle.cart.*;
import sujus.pickle.product.*;
import sujus.pickle.profile.*;
import java.util.UUID;

@Service
public class OrderService {
    private final CartService carts;
    private final CartItemRepository cartItems;
    private final ProductRepository products;
    private final AddressRepository addresses;
    private final OrderRepository orders;
    private final Validator validator;

    public OrderService(CartService carts, CartItemRepository cartItems, ProductRepository products,
                        AddressRepository addresses, OrderRepository orders, Validator validator) {
        this.carts = carts;
        this.cartItems = cartItems;
        this.products = products;
        this.addresses = addresses;
        this.orders = orders;
        this.validator = validator;
    }

    public record Submission(OrderResponse order, boolean replayed) { }

    @Transactional
    public Submission create(Long userId, Long addressId, UUID key) {
        carts.lockUser(userId);
        var previous = orders.findByUserIdAndIdempotencyKey(userId, key);
        if (previous.isPresent()) {
            if (!previous.get().getAddressId().equals(addressId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency key was already used with a different delivery address");
            }
            return new Submission(OrderResponse.from(previous.get()), true);
        }
        Address address = addresses.findByIdAndUser_Id(addressId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery address was not found in your saved addresses"));
        validateAddress(address);
        var items = cartItems.findAllByUserIdOrderByProductIdAsc(userId);
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your cart is empty; add products before checkout");
        }
        CustomerOrder order = new CustomerOrder(userId, key, address);
        // Lock in ascending product ID order across every checkout/import to avoid lock cycles.
        // Any later failure rolls back earlier deductions, the order and the cart clear together.
        for (CartItem item : items) {
            Product product = products.findForUpdateById(item.getProductId()).orElseThrow(() ->
                    new ResponseStatusException(HttpStatus.CONFLICT,
                            "Product " + item.getProductId() + " is no longer available; remove it from your cart"));
            CartService.validateAvailability(product, item.getQuantity());
            order.addItem(product, item.getQuantity());
            product.deductStock(item.getQuantity());
        }
        orders.saveAndFlush(order);
        cartItems.deleteAllByUserId(userId);
        return new Submission(OrderResponse.from(order), false);
    }

    @Transactional(readOnly = true)
    public OrderResponse get(Long userId, Long orderId) {
        return OrderResponse.from(orders.findByIdAndUserId(orderId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Order was not found")));
    }

    @Transactional(readOnly = true)
    public OrderResponse.History history(Long userId, int page, int size) {
        var result = orders.findAllByUserId(userId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new OrderResponse.History(result.getContent().stream().map(OrderResponse::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    private void validateAddress(Address address) {
        AddressRequest request = new AddressRequest(address.getRecipientName(), address.getPhone(),
                address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getState(),
                address.getPostalCode(), address.getCountry(), false);
        if (!validator.validate(request).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Delivery address is incomplete; update your saved address before checkout");
        }
        if (address.getState() == null || !address.getState().trim().equalsIgnoreCase("Kerala")
                || !(address.getCountry().trim().equalsIgnoreCase("India")
                || address.getCountry().trim().equalsIgnoreCase("IN"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery is currently available only in Kerala, India");
        }
    }
}
