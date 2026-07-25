package com.farm2home.order.integration;

import com.farm2home.core.test.BaseIntegrationTest;
import com.farm2home.core.test.AuthenticationTestBuilder;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Order Service using Testcontainers and PostgreSQL.
 * Tests complete order workflows with real database operations.
 */
@DisplayName("Order Service Integration Tests")
class OrderServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID customerId;
    private AuthenticationTestBuilder authBuilder;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        authBuilder = new AuthenticationTestBuilder()
                .withUserId(customerId)
                .withUsername("customer@farm2home.com")
                .withRoles("CUSTOMER");
        orderRepository.deleteAll();
    }

    @Nested
    @DisplayName("Order Creation")
    class OrderCreationTests {

        @Test
        @DisplayName("Should create a new order with items")
        void shouldCreateOrderWithItems() throws Exception {
            var itemRequest = CreateOrderItemRequest.builder()
                    .milkType(MilkType.FULL_CREAM)
                    .quantity(2)
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
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.orderNumber", notNullValue()))
                    .andExpect(jsonPath("$.status").value(OrderStatus.PENDING.name()))
                    .andExpect(jsonPath("$.items", hasSize(1)))
                    .andExpect(jsonPath("$.items[0].milkType").value(MilkType.FULL_CREAM.name()))
                    .andExpect(jsonPath("$.items[0].quantity").value(2));

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
                            .quantity(2)
                            .build(),
                    CreateOrderItemRequest.builder()
                            .milkType(MilkType.TONED)
                            .quantity(1)
                            .build(),
                    CreateOrderItemRequest.builder()
                            .milkType(MilkType.SKIMMED)
                            .quantity(3)
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
                    .andExpect(jsonPath("$.items", hasSize(3)))
                    .andExpect(jsonPath("$.totalAmount", notNullValue()));

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
                    .andExpect(jsonPath("$.id").value(order.getId().toString()))
                    .andExpect(jsonPath("$.orderNumber").value(order.getOrderNumber()))
                    .andExpect(jsonPath("$.status").value(OrderStatus.PENDING.name()));
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
                    .andExpect(jsonPath("$.content", hasSize(2)))
                    .andExpect(jsonPath("$.totalElements").value(2))
                    .andExpect(jsonPath("$.numberOfElements").value(2));
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
        @DisplayName("Should update order status to CONFIRMED")
        void shouldUpdateOrderStatusToConfirmed() throws Exception {
            var order = createTestOrder();
            var updateRequest = new UpdateOrderStatusRequest();
            updateRequest.setStatus(OrderStatus.CONFIRMED);

            mockMvc.perform(patch("/api/v1/orders/" + order.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(OrderStatus.CONFIRMED.name()));

            var updatedOrder = orderRepository.findById(order.getId()).orElseThrow();
            assertThat(updatedOrder.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
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
                    .andExpect(jsonPath("$.status").value(OrderStatus.CANCELLED.name()));
        }

        @Test
        @DisplayName("Should prevent status update for completed order")
        void shouldPreventStatusUpdateForCompletedOrder() throws Exception {
            var order = createTestOrder();
            order.setStatus(OrderStatus.COMPLETED);
            orderRepository.save(order);

            var updateRequest = new UpdateOrderStatusRequest();
            updateRequest.setStatus(OrderStatus.PENDING);

            mockMvc.perform(patch("/api/v1/orders/" + order.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isBadRequest());
        }
    }

    // Helper method to create test orders
    private Order createTestOrder() {
        var order = Order.builder()
                .customerId(customerId)
                .orderNumber("ORD-" + System.currentTimeMillis())
                .orderDate(LocalDate.now())
                .status(OrderStatus.PENDING)
                .totalAmount(BigDecimal.valueOf(160))
                .build();
        return orderRepository.save(order);
    }
}
