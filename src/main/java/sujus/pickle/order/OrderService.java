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
    // Creates and reads customer orders while protecting against duplicate checkout attempts.
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

    // Creates a standard cart-based order and prevents duplicate requests from re-running the purchase.
    @Transactional
    public Submission create(Long userId, Long addressId, UUID key) {
        return create(userId, addressId, null, key);
    }

    // Creates a checkout order using the provided address, while validating idempotency and inventory.
    @Transactional
    public Submission create(Long userId, Long addressId, Long billingAddressId, UUID key) {
        carts.lockUser(userId);
        var previous = orders.findByUserIdAndIdempotencyKey(userId, key);
        if (previous.isPresent()) {
            Long effectiveBillingAddressId = billingAddressId == null ? addressId : billingAddressId;
            if (!previous.get().getAddressId().equals(addressId)
                    || !previous.get().getBillingAddressId().equals(effectiveBillingAddressId)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency key was already used with different checkout details");
            }
            return new Submission(OrderResponse.from(previous.get()), true);
        }
        Address address = addresses.findByIdAndUser_Id(addressId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery address was not found in your saved addresses"));
        validateAddress(address);
        Address billingAddress = billingAddressId == null ? address
                : addresses.findByIdAndUser_Id(billingAddressId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Billing address was not found in your saved addresses"));
        validateBillingAddress(billingAddress);
        var items = cartItems.findAllByUserIdOrderByProductIdAsc(userId);
        if (items.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Your cart is empty; add products before checkout");
        }
        CustomerOrder order = new CustomerOrder(userId, key, address, billingAddress);
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

    // Creates a one-item order directly from a product detail page without first adding it to the cart.
    @Transactional
    public Submission buyNow(Long userId, Long productId, int quantity, Long addressId,
                             Long billingAddressId, UUID key) {
        if (quantity <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Quantity must be a positive integer");
        }
        carts.lockUser(userId);
        Long effectiveBillingAddressId = billingAddressId == null ? addressId : billingAddressId;
        var previous = orders.findByUserIdAndIdempotencyKey(userId, key);
        if (previous.isPresent()) {
            CustomerOrder order = previous.get();
            boolean sameItem = order.getItems().size() == 1
                    && order.getItems().get(0).getProductId().equals(productId)
                    && order.getItems().get(0).getQuantity() == quantity;
            if (!order.getAddressId().equals(addressId)
                    || !order.getBillingAddressId().equals(effectiveBillingAddressId)
                    || !sameItem) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency key was already used with different checkout details");
            }
            return new Submission(OrderResponse.from(order), true);
        }
        Address address = addresses.findByIdAndUser_Id(addressId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Delivery address was not found in your saved addresses"));
        validateAddress(address);
        Address billingAddress = billingAddressId == null ? address
                : addresses.findByIdAndUser_Id(billingAddressId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Billing address was not found in your saved addresses"));
        validateBillingAddress(billingAddress);
        Product product = products.findForUpdateById(productId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Product " + productId + " was not found"));
        CartService.validateAvailability(product, quantity);

        CustomerOrder order = new CustomerOrder(userId, key, address, billingAddress);
        order.addItem(product, quantity);
        product.deductStock(quantity);
        orders.saveAndFlush(order);
        return new Submission(OrderResponse.from(order), false);
    }

    // Returns one order only if it belongs to the authenticated user.
    @Transactional(readOnly = true)
    public OrderResponse get(Long userId, Long orderId) {
        return OrderResponse.from(orders.findByIdAndUserId(orderId, userId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Order was not found")));
    }

    // Returns a paginated history for the current customer.
    @Transactional(readOnly = true)
    public OrderResponse.History history(Long userId, int page, int size) {
        var result = orders.findAllByUserId(userId,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new OrderResponse.History(result.getContent().stream().map(OrderResponse::from).toList(),
                page, size, result.getTotalElements(), result.getTotalPages());
    }

    // Ensures the delivery address is complete and supports only Kerala, India delivery.
    private void validateAddress(Address address) {
        if (!isCompleteAddress(address)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Delivery address is incomplete; update your saved address before checkout");
        }
        if (address.getState() == null || !address.getState().trim().equalsIgnoreCase("Kerala")
                || !(address.getCountry().trim().equalsIgnoreCase("India")
                || address.getCountry().trim().equalsIgnoreCase("IN"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Delivery is currently available only in Kerala, India");
        }
    }

    // Billing addresses must be complete but do not require the Kerala-only restriction.
    private void validateBillingAddress(Address address) {
        if (!isCompleteAddress(address)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Billing address is incomplete; update your saved address before checkout");
        }
    }

    // Reuses bean validation to check whether a saved address is structurally complete.
    private boolean isCompleteAddress(Address address) {
        AddressRequest request = new AddressRequest(address.getRecipientName(), address.getPhone(),
                address.getAddressLine1(), address.getAddressLine2(), address.getCity(), address.getState(),
                address.getPostalCode(), address.getCountry(), false);
        return validator.validate(request).isEmpty();
    }
}
