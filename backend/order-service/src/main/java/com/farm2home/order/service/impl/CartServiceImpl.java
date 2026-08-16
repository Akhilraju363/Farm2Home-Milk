package com.farm2home.order.service.impl;

import com.farm2home.order.client.InventoryServiceClient;
import com.farm2home.order.client.ProductDetailResponse;
import com.farm2home.order.domain.entity.Cart;
import com.farm2home.order.domain.entity.CartItem;
import com.farm2home.order.domain.repository.CartItemRepository;
import com.farm2home.order.domain.repository.CartRepository;
import com.farm2home.order.dto.request.AddCartItemRequest;
import com.farm2home.order.dto.request.UpdateCartItemRequest;
import com.farm2home.order.dto.response.CartItemResponse;
import com.farm2home.order.dto.response.CartResponse;
import com.farm2home.order.exception.OrderException;
import com.farm2home.order.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientException;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Cart is deliberately product-only (no milkType support - see CartItem's own comment) and holds
 * no price/name snapshot: everything customer-facing is resolved live from inventory-service on
 * every read, since a cart item's price/availability isn't final until checkout (see
 * OrderServiceImpl.checkout(), which re-resolves everything again anyway - the same product is
 * never trusted twice from the same stale source).
 */
@Service
@RequiredArgsConstructor
public class CartServiceImpl {

    private static final BigDecimal MAX_PRODUCT_QUANTITY = new BigDecimal("50");

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final InventoryServiceClient inventoryServiceClient;

    @Transactional
    public CartResponse getCart(UUID customerId) {
        return toResponse(getOrCreateCart(customerId));
    }

    @Transactional
    public CartResponse addItem(UUID customerId, AddCartItemRequest request) {
        validateQuantity(request.getQuantity());
        Cart cart = getOrCreateCart(customerId);
        ProductDetailResponse product = resolveProduct(request.getProductId());
        assertPurchasable(product);

        CartItem existing = cartItemRepository.findByCart_IdAndProductId(cart.getId(), request.getProductId()).orElse(null);
        BigDecimal newQuantity = existing != null ? existing.getQuantity().add(request.getQuantity()) : request.getQuantity();
        assertWithinLimitsAndStock(newQuantity, product);

        if (existing != null) {
            existing.setQuantity(newQuantity);
            cartItemRepository.save(existing);
        } else {
            CartItem item = CartItem.builder().productId(request.getProductId()).quantity(newQuantity).build();
            cart.addItem(item);
            cartRepository.save(cart);
        }
        return toResponse(getOrCreateCart(customerId));
    }

    @Transactional
    public CartResponse updateItem(UUID customerId, UUID itemId, UpdateCartItemRequest request) {
        validateQuantity(request.getQuantity());
        CartItem item = cartItemRepository.findByIdAndCart_CustomerId(itemId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("This product is no longer in your cart."));

        ProductDetailResponse product = resolveProduct(item.getProductId());
        assertPurchasable(product);
        assertWithinLimitsAndStock(request.getQuantity(), product);

        item.setQuantity(request.getQuantity());
        cartItemRepository.save(item);
        return toResponse(getOrCreateCart(customerId));
    }

    @Transactional
    public CartResponse removeItem(UUID customerId, UUID itemId) {
        CartItem item = cartItemRepository.findByIdAndCart_CustomerId(itemId, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("This product is no longer in your cart."));
        cartItemRepository.delete(item);
        return toResponse(getOrCreateCart(customerId));
    }

    @Transactional
    public void clearCart(UUID customerId) {
        Cart cart = getOrCreateCart(customerId);
        cart.getItems().clear();
        cartRepository.save(cart);
    }

    private Cart getOrCreateCart(UUID customerId) {
        return cartRepository.findByCustomerId(customerId)
                .orElseGet(() -> cartRepository.save(Cart.builder().customerId(customerId).build()));
    }

    private void validateQuantity(BigDecimal quantity) {
        if (quantity == null || quantity.signum() <= 0 || quantity.compareTo(MAX_PRODUCT_QUANTITY) > 0) {
            throw new OrderException(String.format("Quantity must be greater than 0 and at most %s.", MAX_PRODUCT_QUANTITY));
        }
    }

    private void assertPurchasable(ProductDetailResponse product) {
        if (!product.isActive() || !product.isAvailability()) {
            throw new OrderException("\"" + product.getName() + "\" is no longer available.");
        }
    }

    private void assertWithinLimitsAndStock(BigDecimal quantity, ProductDetailResponse product) {
        if (quantity.compareTo(MAX_PRODUCT_QUANTITY) > 0) {
            throw new OrderException(String.format("Quantity must be at most %s.", MAX_PRODUCT_QUANTITY));
        }
        if (product.getStockQuantity() != null && quantity.compareTo(BigDecimal.valueOf(product.getStockQuantity())) > 0) {
            throw new OrderException("Only " + product.getStockQuantity() + " " + product.getName() + " available right now.");
        }
    }

    private ProductDetailResponse resolveProduct(UUID productId) {
        ProductDetailResponse product;
        try {
            product = inventoryServiceClient.getProduct(productId).block();
        } catch (WebClientException ex) {
            throw new OrderException("Could not verify product details right now. Please try again.");
        }
        if (product == null) {
            throw new ResourceNotFoundException("Product not found: " + productId);
        }
        return product;
    }

    /** Best-effort per item - a product that no longer resolves (deleted, or inventory-service
     *  unreachable) doesn't fail the whole cart read; that line is just marked unavailable and
     *  excluded from the subtotal, same as a real cart would show a stale/removed listing. */
    private CartResponse toResponse(Cart cart) {
        List<CartItemResponse> items = cart.getItems().stream()
                .map(this::toItemResponse)
                .toList();
        BigDecimal subtotal = items.stream()
                .filter(CartItemResponse::isAvailable)
                .map(CartItemResponse::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return CartResponse.builder()
                .cartId(cart.getId())
                .items(items)
                .subtotal(subtotal)
                .itemCount(items.size())
                .build();
    }

    private CartItemResponse toItemResponse(CartItem item) {
        ProductDetailResponse product;
        try {
            product = inventoryServiceClient.getProduct(item.getProductId()).block();
        } catch (WebClientException ex) {
            product = null;
        }
        if (product == null) {
            return CartItemResponse.builder()
                    .id(item.getId()).productId(item.getProductId()).quantity(item.getQuantity())
                    .available(false).unavailableReason("This product is no longer available.")
                    .build();
        }
        if (!product.isActive() || !product.isAvailability()) {
            return CartItemResponse.builder()
                    .id(item.getId()).productId(item.getProductId()).productName(product.getName())
                    .imageUrl(product.getImageUrl()).quantity(item.getQuantity()).unit(product.getUnit())
                    .available(false).unavailableReason("\"" + product.getName() + "\" is no longer available.")
                    .build();
        }
        BigDecimal subtotal = item.getQuantity().multiply(product.getPrice());
        return CartItemResponse.builder()
                .id(item.getId()).productId(item.getProductId()).productName(product.getName())
                .imageUrl(product.getImageUrl()).quantity(item.getQuantity()).unit(product.getUnit())
                .unitPrice(product.getPrice()).subtotal(subtotal).available(true)
                .build();
    }
}
