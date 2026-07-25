package com.farm2home.order.service;

import com.farm2home.order.config.MilkPriceProperties;
import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.entity.OrderItem;
import com.farm2home.order.domain.enums.MilkType;
import com.farm2home.order.domain.enums.OrderStatus;
import com.farm2home.order.domain.enums.OrderType;
import com.farm2home.order.domain.repository.OrderRepository;
import com.farm2home.order.dto.request.CreateOrderItemRequest;
import com.farm2home.order.dto.request.CreateOrderRequest;
import com.farm2home.order.dto.request.UpdateOrderStatusRequest;
import com.farm2home.order.dto.response.OrderResponse;
import com.farm2home.order.exception.OrderException;
import com.farm2home.order.exception.ResourceNotFoundException;
import com.farm2home.order.kafka.OrderEventProducer;
import com.farm2home.order.mapper.OrderMapper;
import com.farm2home.order.service.impl.OrderServiceImpl;
import com.farm2home.common.core.audit.AuditLogService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock private OrderRepository orderRepository;
    @Mock private MilkPriceProperties priceProperties;
    @Mock private OrderMapper orderMapper;
    @Mock private OrderEventProducer eventProducer;
    @Mock private AuditLogService auditLogService;

    @InjectMocks private OrderServiceImpl service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID orderId    = UUID.randomUUID();

    @BeforeEach
    void setupPrices() {
        // lenient: only the Create tests actually invoke getPriceFor(); MockitoExtension's
        // strict stubbing would otherwise flag this shared setup as unused in every other
        // nested test class.
        lenient().when(priceProperties.getPriceFor("FULL_CREAM")).thenReturn(new BigDecimal("80.00"));
        lenient().when(priceProperties.getPriceFor("TONED")).thenReturn(new BigDecimal("65.00"));
    }

    private Order buildPendingOrder() {
        return Order.builder()
                .id(orderId)
                .orderNumber("ORD-2026-100001")
                .customerId(customerId)
                .orderDate(LocalDate.now())
                .orderType(OrderType.ONE_TIME)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("120.00"))
                .items(new ArrayList<>())
                .deleted(false)
                .build();
    }

    private OrderResponse buildResponse(OrderStatus status) {
        return OrderResponse.builder()
                .id(orderId)
                .orderNumber("ORD-2026-100001")
                .status(status.name())
                .build();
    }

    // ── Create ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createManualOrder()")
    class Create {

        @Test
        @DisplayName("valid request → creates order with correct total amount")
        void happyPath() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .orderDate(LocalDate.now())
                    .items(List.of(
                            CreateOrderItemRequest.builder()
                                    .milkType(MilkType.FULL_CREAM).quantity(new BigDecimal("1.5")).build()))
                    .build();

            Order saved = buildPendingOrder();
            when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
            when(orderMapper.toItemEntity(any())).thenReturn(new OrderItem());
            when(orderRepository.nextOrderNumber()).thenReturn(100001L);
            when(orderRepository.save(any())).thenReturn(saved);
            when(orderMapper.toResponse(saved)).thenReturn(buildResponse(OrderStatus.PENDING));

            OrderResponse result = service.createManualOrder(req, customerId);

            assertThat(result.getStatus()).isEqualTo("PENDING");
            verify(orderRepository).save(any(Order.class));
            verify(eventProducer).publishOrderCreated(saved);
        }

        @Test
        @DisplayName("two items → total is sum of both")
        void multipleItems_totalsCorrect() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .orderDate(LocalDate.now())
                    .items(List.of(
                            CreateOrderItemRequest.builder()
                                    .milkType(MilkType.FULL_CREAM).quantity(new BigDecimal("1.0")).build(),
                            CreateOrderItemRequest.builder()
                                    .milkType(MilkType.TONED).quantity(new BigDecimal("2.0")).build()))
                    .build();

            when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
            when(orderMapper.toItemEntity(any())).thenReturn(new OrderItem());
            when(orderRepository.nextOrderNumber()).thenReturn(100002L);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(UUID.randomUUID()); // real repository.save() always assigns an id
                return o;
            });
            when(orderMapper.toResponse(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                return OrderResponse.builder().totalAmount(o.getTotalAmount()).build();
            });

            OrderResponse result = service.createManualOrder(req, customerId);

            // 1.0 × 80 + 2.0 × 65 = 80 + 130 = 210
            assertThat(result.getTotalAmount()).isEqualByComparingTo("210.00");
        }

        @Test
        @DisplayName("empty items list → throws OrderException")
        void emptyItems() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .orderDate(LocalDate.now())
                    .items(List.of())
                    .build();

            assertThatThrownBy(() -> service.createManualOrder(req, customerId))
                    .isInstanceOf(OrderException.class)
                    .hasMessageContaining("at least one item");
            verifyNoInteractions(orderRepository);
        }
    }

    // ── FindById ─────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("customer owns order → returns response")
        void customerOwns() {
            Order order = buildPendingOrder();
            when(orderRepository.findByIdAndCustomerIdAndDeletedFalse(orderId, customerId))
                    .thenReturn(Optional.of(order));
            when(orderMapper.toResponse(order)).thenReturn(buildResponse(OrderStatus.PENDING));

            OrderResponse result = service.findById(orderId, customerId);
            assertThat(result.getId()).isEqualTo(orderId);
        }

        @Test
        @DisplayName("wrong customer → throws ResourceNotFoundException")
        void wrongCustomer() {
            when(orderRepository.findByIdAndCustomerIdAndDeletedFalse(orderId, customerId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(orderId, customerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("admin (null customerId) → queries without customer filter")
        void adminAccess() {
            Order order = buildPendingOrder();
            when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.of(order));
            when(orderMapper.toResponse(order)).thenReturn(buildResponse(OrderStatus.PENDING));

            service.findById(orderId, null);

            verify(orderRepository).findByIdAndDeletedFalse(orderId);
            verify(orderRepository, never()).findByIdAndCustomerIdAndDeletedFalse(any(), any());
        }
    }

    // ── UpdateStatus ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("PENDING → ASSIGNED is a valid transition")
        void pendingToAssigned() {
            Order order = buildPendingOrder();
            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.ASSIGNED);

            when(orderRepository.findByIdAndCustomerIdAndDeletedFalse(orderId, customerId))
                    .thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);
            when(orderMapper.toResponse(order)).thenReturn(buildResponse(OrderStatus.ASSIGNED));

            OrderResponse result = service.updateStatus(orderId, req, customerId, false, customerId);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
            assertThat(result.getStatus()).isEqualTo("ASSIGNED");
        }

        @Test
        @DisplayName("PENDING → DELIVERED is an invalid transition")
        void invalidTransition() {
            Order order = buildPendingOrder();
            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.DELIVERED);

            when(orderRepository.findByIdAndCustomerIdAndDeletedFalse(orderId, customerId))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.updateStatus(orderId, req, customerId, false, customerId))
                    .isInstanceOf(OrderException.class)
                    .hasMessageContaining("Invalid status transition");
        }

        @Test
        @DisplayName("DELIVERED → CANCELLED is invalid (terminal status)")
        void fromTerminal() {
            Order order = buildPendingOrder();
            order.setStatus(OrderStatus.DELIVERED);
            UpdateOrderStatusRequest req = new UpdateOrderStatusRequest();
            req.setStatus(OrderStatus.CANCELLED);

            when(orderRepository.findByIdAndCustomerIdAndDeletedFalse(orderId, customerId))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.updateStatus(orderId, req, customerId, false, customerId))
                    .isInstanceOf(OrderException.class)
                    .hasMessageContaining("Invalid status transition");
        }
    }

    // ── Cancel ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("cancel()")
    class Cancel {

        @Test
        @DisplayName("PENDING order → sets CANCELLED")
        void cancelPending() {
            Order order = buildPendingOrder();
            when(orderRepository.findByIdAndCustomerIdAndDeletedFalse(orderId, customerId))
                    .thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            service.cancel(orderId, customerId, false, customerId);

            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }

        @Test
        @DisplayName("DELIVERED order → throws OrderException")
        void cancelDelivered() {
            Order order = buildPendingOrder();
            order.setStatus(OrderStatus.DELIVERED);

            when(orderRepository.findByIdAndCustomerIdAndDeletedFalse(orderId, customerId))
                    .thenReturn(Optional.of(order));

            assertThatThrownBy(() -> service.cancel(orderId, customerId, false, customerId))
                    .isInstanceOf(OrderException.class)
                    .hasMessageContaining("DELIVERED");
        }

        @Test
        @DisplayName("admin bypasses ownership check")
        void adminBypassesOwnership() {
            Order order = buildPendingOrder();
            when(orderRepository.findByIdAndDeletedFalse(orderId)).thenReturn(Optional.of(order));
            when(orderRepository.save(order)).thenReturn(order);

            service.cancel(orderId, null, true, customerId);

            verify(orderRepository).findByIdAndDeletedFalse(orderId);
        }
    }
}
