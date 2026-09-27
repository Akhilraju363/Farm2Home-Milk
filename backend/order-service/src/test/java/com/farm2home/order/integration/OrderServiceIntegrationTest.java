package com.farm2home.order.integration;

import com.farm2home.core.test.BaseIntegrationTest;
import com.farm2home.core.test.AuthenticationTestBuilder;
import com.farm2home.order.client.CustomerServiceClient;
import com.farm2home.order.client.DailyProductionResponse;
import com.farm2home.order.client.DeliveryAvailabilityResponse;
import com.farm2home.order.client.ProductionServiceClient;
import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.enums.MilkType;
import com.farm2home.order.domain.enums.OrderStatus;
import com.farm2home.order.domain.repository.OrderRepository;
import com.farm2home.order.dto.request.CreateOrderItemRequest;
import com.farm2home.order.dto.request.CreateOrderRequest;
import com.farm2home.order.dto.request.UpdateOrderStatusRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Order Service using Testcontainers and PostgreSQL.
 * Tests complete order workflows with real database operations.
 *
 * <p>Only the two cross-service HTTP clients the manual-order path calls are mocked - production-service
 * (same-day milk capacity) and customer-service (delivery availability) are separate deployables that
 * don't exist in this test's context. Everything inside order-service itself (controller, security
 * filter, service, JPA, Flyway schema) runs for real.
 */
@DisplayName("Order Service Integration Tests")
class OrderServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductionServiceClient productionServiceClient;

    @MockBean
    private CustomerServiceClient customerServiceClient;

    private UUID customerId;
    private AuthenticationTestBuilder authBuilder;
    private AuthenticationTestBuilder adminAuthBuilder;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        authBuilder = new AuthenticationTestBuilder()
                .withUserId(customerId)
                .withMobile("9876543210")
                .withRoles("CUSTOMER");
        adminAuthBuilder = new AuthenticationTestBuilder()
                .withMobile("9876500000")
                .withRoles("FARM_MANAGER");
        orderRepository.deleteAll();

        // Today's production comfortably covers every order these tests place, so the same-day
        // capacity check runs (and passes) instead of being bypassed with a future order date.
        var todaysProduction = new DailyProductionResponse();
        todaysProduction.setDate(LocalDate.now());
        todaysProduction.setTotalLiters(BigDecimal.valueOf(100));
        when(productionServiceClient.getDailySummary(any(), any())).thenReturn(Mono.just(List.of(todaysProduction)));

        var availability = new DeliveryAvailabilityResponse();
        availability.setDeliveryAvailable(true);
        when(customerServiceClient.getDeliveryAvailability(any())).thenReturn(Mono.just(availability));
    }

    @Nested
    @DisplayName("Order Creation")
    class OrderCreationTests {

        @Test
        @DisplayName("Should create a new order with items")
        void shouldCreateOrderWithItems() throws Exception {
            var itemRequest = CreateOrderItemRequest.builder()
                    .milkType(MilkType.FULL_CREAM)
                    .quantity(BigDecimal.valueOf(2))
                    .build();

            var orderRequest = CreateOrderRequest.builder()
                    .orderDate(LocalDate.now())
                    .items(List.of(itemRequest))
                    .build();

            mockMvc.perform(post("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(orderRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id", notNullValue()))
                    .andExpect(jsonPath("$.data.orderNumber", notNullValue()))
                    .andExpect(jsonPath("$.data.status").value(OrderStatus.PENDING.name()))
                    .andExpect(jsonPath("$.data.items", hasSize(1)))
                    .andExpect(jsonPath("$.data.items[0].milkType").value(MilkType.FULL_CREAM.name()))
                    .andExpect(jsonPath("$.data.items[0].quantity").value(2));

            var savedOrders = orderRepository.findAll();
            assertThat(savedOrders).hasSize(1);
            assertThat(savedOrders.get(0).getCustomerId()).isEqualTo(customerId);
        }

        @Test
        @DisplayName("Should create order with multiple milk types")
        void shouldCreateOrderWithMultipleMilkTypes() throws Exception {
            var items = List.of(
                    CreateOrderItemRequest.builder()
                            .milkType(MilkType.FULL_CREAM)
                            .quantity(BigDecimal.valueOf(2))
                            .build(),
                    CreateOrderItemRequest.builder()
                            .milkType(MilkType.TONED)
                            .quantity(BigDecimal.valueOf(1))
                            .build(),
                    CreateOrderItemRequest.builder()
                            .milkType(MilkType.SKIMMED)
                            .quantity(BigDecimal.valueOf(3))
                            .build()
            );

            var orderRequest = CreateOrderRequest.builder()
                    .orderDate(LocalDate.now())
                    .items(items)
                    .build();

            mockMvc.perform(post("/api/v1/orders")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(orderRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.items", hasSize(3)))
                    .andExpect(jsonPath("$.data.totalAmount", notNullValue()));

            var savedOrders = orderRepository.findAll();
            assertThat(savedOrders).hasSize(1);
            assertThat(savedOrders.get(0).getItems()).hasSize(3);
        }
    }

    @Nested
    @DisplayName("Order Retrieval")
    class OrderRetrievalTests {

        @Test
        @DisplayName("Should retrieve order by ID")
        void shouldRetrieveOrderById() throws Exception {
            var order = createTestOrder();

            mockMvc.perform(get("/api/v1/orders/" + order.getId())
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(order.getId().toString()))
                    .andExpect(jsonPath("$.data.orderNumber").value(order.getOrderNumber()))
                    .andExpect(jsonPath("$.data.status").value(OrderStatus.PENDING.name()));
        }

        @Test
        @DisplayName("Should retrieve paginated orders for customer")
        void shouldRetrieveCustomerOrders() throws Exception {
            createTestOrder();
            createTestOrder();

            mockMvc.perform(get("/api/v1/orders")
                    .param("page", "0")
                    .param("size", "10")
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.totalElements").value(2))
                    .andExpect(jsonPath("$.data.numberOfElements").value(2));
        }

        @Test
        @DisplayName("Should return 404 for non-existent order")
        void shouldReturn404ForNonExistentOrder() throws Exception {
            mockMvc.perform(get("/api/v1/orders/" + UUID.randomUUID())
                    .with(authBuilder.build()))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("Order Status Updates")
    class OrderStatusUpdateTests {

        @Test
        @DisplayName("Should update order status to ASSIGNED")
        void shouldUpdateOrderStatusToAssigned() throws Exception {
            var order = createTestOrder();
            var updateRequest = new UpdateOrderStatusRequest();
            updateRequest.setStatus(OrderStatus.ASSIGNED);

            mockMvc.perform(patch("/api/v1/orders/" + order.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(adminAuthBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(OrderStatus.ASSIGNED.name()));

            var updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.ASSIGNED);
        }

        @Test
        @DisplayName("Should update order status to CANCELLED")
        void shouldUpdateOrderStatusToCancelled() throws Exception {
            var order = createTestOrder();
            var updateRequest = new UpdateOrderStatusRequest();
            updateRequest.setStatus(OrderStatus.CANCELLED);

            mockMvc.perform(patch("/api/v1/orders/" + order.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(OrderStatus.CANCELLED.name()));
        }

        @Test
        @DisplayName("Should prevent status update for delivered order")
        void shouldPreventStatusUpdateForDeliveredOrder() throws Exception {
            var order = createTestOrder();
            order.setStatus(OrderStatus.DELIVERED);
            orderRepository.save(order);

            var updateRequest = new UpdateOrderStatusRequest();
            updateRequest.setStatus(OrderStatus.PENDING);

            mockMvc.perform(patch("/api/v1/orders/" + order.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    // OrderException is @ResponseStatus(UNPROCESSABLE_ENTITY)
                    .andExpect(status().isUnprocessableEntity());
        }
    }

    // Helper method to create test orders
    private Order createTestOrder() {
        var order = Order.builder()
                .customerId(customerId)
                .orderNumber("ORD-TEST-" + UUID.randomUUID().toString().substring(0, 8))
                .orderDate(LocalDate.now())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(160))
                .build();
        return orderRepository.save(order);
    }
}
