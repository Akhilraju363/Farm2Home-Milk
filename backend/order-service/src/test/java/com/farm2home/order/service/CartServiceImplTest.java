package com.farm2home.order.service;

import com.farm2home.order.client.InventoryServiceClient;
import com.farm2home.order.client.ProductDetailResponse;
import com.farm2home.order.domain.entity.Cart;
import com.farm2home.order.domain.entity.CartItem;
import com.farm2home.order.domain.repository.CartItemRepository;
import com.farm2home.order.domain.repository.CartRepository;
import com.farm2home.order.dto.request.AddCartItemRequest;
import com.farm2home.order.dto.request.UpdateCartItemRequest;
import com.farm2home.order.dto.response.CartResponse;
import com.farm2home.order.exception.OrderException;
import com.farm2home.order.exception.ResourceNotFoundException;
import com.farm2home.order.service.impl.CartServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock private CartRepository cartRepository;
    @Mock private CartItemRepository cartItemRepository;
    @Mock private InventoryServiceClient inventoryServiceClient;

    @InjectMocks private CartServiceImpl service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID cartId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private Cart emptyCart() {
        return Cart.builder().id(cartId).customerId(customerId).build();
    }

    private ProductDetailResponse activeProduct(String name, String price, int stock) {
        ProductDetailResponse p = new ProductDetailResponse();
        p.setId(productId);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setUnit("L");
        p.setActive(true);
        p.setAvailability(true);
        p.setStockQuantity(stock);
        return p;
    }

    @Nested
    @DisplayName("getCart()")
    class GetCart {

        @Test
        @DisplayName("no cart yet -> creates one, returns empty response")
        void createsOnFirstAccess() {
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenAnswer(inv -> {
                Cart c = inv.getArgument(0);
                c.setId(cartId);
                return c;
            });

            CartResponse result = service.getCart(customerId);

            assertThat(result.getCartId()).isEqualTo(cartId);
            assertThat(result.getItems()).isEmpty();
            assertThat(result.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }

        @Test
        @DisplayName("existing cart with a live product -> resolves name/price/unit, computes subtotal")
        void resolvesLiveProductData() {
            Cart cart = emptyCart();
            cart.addItem(CartItem.builder().id(UUID.randomUUID()).productId(productId).quantity(new BigDecimal("3")).build());
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(inventoryServiceClient.getProduct(productId)).thenReturn(Mono.just(activeProduct("Full Cream Milk 1L", "70.00", 20)));

            CartResponse result = service.getCart(customerId);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).isAvailable()).isTrue();
            assertThat(result.getItems().get(0).getSubtotal()).isEqualByComparingTo("210.00");
            assertThat(result.getSubtotal()).isEqualByComparingTo("210.00");
            assertThat(result.getItemCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("a cart item whose product was deleted -> marked unavailable, excluded from subtotal")
        void deletedProductLine_excludedFromSubtotal() {
            Cart cart = emptyCart();
            cart.addItem(CartItem.builder().id(UUID.randomUUID()).productId(productId).quantity(BigDecimal.ONE).build());
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(inventoryServiceClient.getProduct(productId)).thenReturn(Mono.empty());

            CartResponse result = service.getCart(customerId);

            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).isAvailable()).isFalse();
            assertThat(result.getSubtotal()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("addItem()")
    class AddItem {

        @Test
        @DisplayName("new product -> creates a CartItem")
        void newProduct_addsItem() {
            Cart cart = emptyCart();
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(inventoryServiceClient.getProduct(productId)).thenReturn(Mono.just(activeProduct("Farm Eggs", "90.00", 10)));
            when(cartItemRepository.findByCart_IdAndProductId(cartId, productId)).thenReturn(Optional.empty());
            when(cartRepository.save(any(Cart.class))).thenReturn(cart);

            AddCartItemRequest req = new AddCartItemRequest();
            req.setProductId(productId);
            req.setQuantity(new BigDecimal("2"));

            service.addItem(customerId, req);

            verify(cartRepository, atLeastOnce()).save(any(Cart.class));
        }

        @Test
        @DisplayName("product already in cart -> quantity is added to the existing line, not duplicated")
        void existingProduct_addsToExistingQuantity() {
            Cart cart = emptyCart();
            CartItem existing = CartItem.builder().id(UUID.randomUUID()).productId(productId).quantity(new BigDecimal("2")).build();
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(inventoryServiceClient.getProduct(productId)).thenReturn(Mono.just(activeProduct("Farm Eggs", "90.00", 10)));
            when(cartItemRepository.findByCart_IdAndProductId(cartId, productId)).thenReturn(Optional.of(existing));

            AddCartItemRequest req = new AddCartItemRequest();
            req.setProductId(productId);
            req.setQuantity(new BigDecimal("3"));

            service.addItem(customerId, req);

            verify(cartItemRepository).save(argThat(ci -> ci.getQuantity().compareTo(new BigDecimal("5")) == 0));
            verify(cartRepository, never()).save(any(Cart.class));
        }

        @Test
        @DisplayName("inactive product -> OrderException, nothing saved")
        void inactiveProduct_throws() {
            Cart cart = emptyCart();
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            ProductDetailResponse inactive = activeProduct("Seasonal Butter", "150.00", 10);
            inactive.setActive(false);
            when(inventoryServiceClient.getProduct(productId)).thenReturn(Mono.just(inactive));

            AddCartItemRequest req = new AddCartItemRequest();
            req.setProductId(productId);
            req.setQuantity(BigDecimal.ONE);

            assertThatThrownBy(() -> service.addItem(customerId, req))
                    .isInstanceOf(OrderException.class)
                    .hasMessageContaining("no longer available");
            verify(cartItemRepository, never()).save(any());
        }

        @Test
        @DisplayName("quantity exceeds current stock -> OrderException")
        void exceedsStock_throws() {
            Cart cart = emptyCart();
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(inventoryServiceClient.getProduct(productId)).thenReturn(Mono.just(activeProduct("Farm Eggs", "90.00", 3)));
            when(cartItemRepository.findByCart_IdAndProductId(cartId, productId)).thenReturn(Optional.empty());

            AddCartItemRequest req = new AddCartItemRequest();
            req.setProductId(productId);
            req.setQuantity(new BigDecimal("5"));

            assertThatThrownBy(() -> service.addItem(customerId, req))
                    .isInstanceOf(OrderException.class)
                    .hasMessageContaining("Only 3");
        }

        @Test
        @DisplayName("product not found -> ResourceNotFoundException")
        void productNotFound_throws() {
            Cart cart = emptyCart();
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(inventoryServiceClient.getProduct(productId)).thenReturn(Mono.empty());

            AddCartItemRequest req = new AddCartItemRequest();
            req.setProductId(productId);
            req.setQuantity(BigDecimal.ONE);

            assertThatThrownBy(() -> service.addItem(customerId, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("updateItem()")
    class UpdateItem {

        @Test
        @DisplayName("owned item, valid quantity -> updates")
        void ownedItem_updates() {
            UUID itemId = UUID.randomUUID();
            CartItem item = CartItem.builder().id(itemId).productId(productId).quantity(new BigDecimal("1")).cart(emptyCart()).build();
            when(cartItemRepository.findByIdAndCart_CustomerId(itemId, customerId)).thenReturn(Optional.of(item));
            when(inventoryServiceClient.getProduct(productId)).thenReturn(Mono.just(activeProduct("Farm Eggs", "90.00", 10)));
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(item.getCart()));

            UpdateCartItemRequest req = new UpdateCartItemRequest();
            req.setQuantity(new BigDecimal("4"));

            service.updateItem(customerId, itemId, req);

            assertThat(item.getQuantity()).isEqualByComparingTo("4");
            verify(cartItemRepository).save(item);
        }

        @Test
        @DisplayName("item belongs to a different customer -> ResourceNotFoundException (404, not 403)")
        void crossCustomerAccess_throwsNotFound() {
            UUID itemId = UUID.randomUUID();
            when(cartItemRepository.findByIdAndCart_CustomerId(itemId, customerId)).thenReturn(Optional.empty());

            UpdateCartItemRequest req = new UpdateCartItemRequest();
            req.setQuantity(BigDecimal.ONE);

            assertThatThrownBy(() -> service.updateItem(customerId, itemId, req))
                    .isInstanceOf(ResourceNotFoundException.class)
                    .hasMessageContaining("no longer in your cart");
        }
    }

    @Nested
    @DisplayName("removeItem()")
    class RemoveItem {

        @Test
        @DisplayName("owned item -> removed")
        void ownedItem_removed() {
            UUID itemId = UUID.randomUUID();
            CartItem item = CartItem.builder().id(itemId).productId(productId).cart(emptyCart()).build();
            when(cartItemRepository.findByIdAndCart_CustomerId(itemId, customerId)).thenReturn(Optional.of(item));
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(item.getCart()));

            service.removeItem(customerId, itemId);

            verify(cartItemRepository).delete(item);
        }

        @Test
        @DisplayName("item belongs to a different customer -> ResourceNotFoundException")
        void crossCustomerAccess_throwsNotFound() {
            UUID itemId = UUID.randomUUID();
            when(cartItemRepository.findByIdAndCart_CustomerId(itemId, customerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.removeItem(customerId, itemId))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(cartItemRepository, never()).delete(any());
        }
    }

    @Nested
    @DisplayName("clearCart()")
    class ClearCart {

        @Test
        @DisplayName("removes every item")
        void removesAllItems() {
            Cart cart = emptyCart();
            cart.addItem(CartItem.builder().id(UUID.randomUUID()).productId(productId).quantity(BigDecimal.ONE).build());
            when(cartRepository.findByCustomerId(customerId)).thenReturn(Optional.of(cart));
            when(cartRepository.save(cart)).thenReturn(cart);

            service.clearCart(customerId);

            assertThat(cart.getItems()).isEmpty();
            verify(cartRepository).save(cart);
        }
    }
}
