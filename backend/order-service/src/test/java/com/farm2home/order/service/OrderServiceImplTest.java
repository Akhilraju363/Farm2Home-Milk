package com.farm2home.order.service;

import com.farm2home.order.client.DailyProductionResponse;
import com.farm2home.order.client.ProductionServiceClient;
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
import com.farm2home.common.core.dashboard.OrderSummaryResponse;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SalesReportRow;
import com.farm2home.common.core.reports.SalesReportSummary;
import com.farm2home.common.export.ExportFormat;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import jakarta.persistence.criteria.CriteriaQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
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
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private EntityManager entityManager;
    @Mock private ProductionServiceClient productionServiceClient;

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

    /** Same-day orders (the only ones the production-capacity check applies to) need
     *  production-service and the already-ordered-today total stubbed, or the check NPEs on a
     *  null Mono. Stubs a generously large produced total so the check always passes. */
    private void stubSufficientProduction() {
        DailyProductionResponse summary = new DailyProductionResponse();
        summary.setDate(LocalDate.now());
        summary.setTotalLiters(new BigDecimal("1000.00"));
        lenient().when(productionServiceClient.getDailySummary(LocalDate.now(), LocalDate.now()))
                .thenReturn(Mono.just(List.of(summary)));
        lenient().when(orderRepository.sumOrderedQuantityByDate(LocalDate.now())).thenReturn(BigDecimal.ZERO);
    }

    // ── Create ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("createManualOrder()")
    class Create {

        @Test
        @DisplayName("valid request → creates order with correct total amount")
        void happyPath() {
            stubSufficientProduction();
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
            stubSufficientProduction();
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

        @Test
        @DisplayName("same-day order exceeding today's recorded production → throws OrderException")
        void sameDayOrder_exceedsProduction_throws() {
            DailyProductionResponse summary = new DailyProductionResponse();
            summary.setDate(LocalDate.now());
            summary.setTotalLiters(new BigDecimal("2.00"));
            when(productionServiceClient.getDailySummary(LocalDate.now(), LocalDate.now()))
                    .thenReturn(Mono.just(List.of(summary)));
            when(orderRepository.sumOrderedQuantityByDate(LocalDate.now())).thenReturn(new BigDecimal("1.00"));

            CreateOrderRequest req = CreateOrderRequest.builder()
                    .orderDate(LocalDate.now())
                    .items(List.of(CreateOrderItemRequest.builder()
                            .milkType(MilkType.FULL_CREAM).quantity(new BigDecimal("2.00")).build()))
                    .build();

            assertThatThrownBy(() -> service.createManualOrder(req, customerId))
                    .isInstanceOf(OrderException.class)
                    .hasMessageContaining("Insufficient milk production");
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("future-dated order → skips the production check entirely")
        void futureDatedOrder_skipsProductionCheck() {
            CreateOrderRequest req = CreateOrderRequest.builder()
                    .orderDate(LocalDate.now().plusDays(3))
                    .items(List.of(CreateOrderItemRequest.builder()
                            .milkType(MilkType.FULL_CREAM).quantity(new BigDecimal("50.00")).build()))
                    .build();

            when(orderMapper.toEntity(req)).thenReturn(Order.builder().build());
            when(orderMapper.toItemEntity(any())).thenReturn(new OrderItem());
            when(orderRepository.nextOrderNumber()).thenReturn(100003L);
            when(orderRepository.save(any())).thenAnswer(inv -> {
                Order o = inv.getArgument(0);
                o.setId(UUID.randomUUID());
                return o;
            });
            when(orderMapper.toResponse(any())).thenReturn(buildResponse(OrderStatus.PENDING));

            service.createManualOrder(req, customerId);

            verifyNoInteractions(productionServiceClient);
            verify(orderRepository, never()).sumOrderedQuantityByDate(any());
        }

        @Test
        @DisplayName("production-service unreachable for a same-day order → throws OrderException")
        void productionServiceUnreachable_throws() {
            when(productionServiceClient.getDailySummary(LocalDate.now(), LocalDate.now())).thenReturn(Mono.error(
                    new org.springframework.web.reactive.function.client.WebClientRequestException(
                            new java.net.ConnectException("connection refused"),
                            org.springframework.http.HttpMethod.GET,
                            java.net.URI.create("http://production-service/api/v1/productions/summary"),
                            new org.springframework.http.HttpHeaders())));

            CreateOrderRequest req = CreateOrderRequest.builder()
                    .orderDate(LocalDate.now())
                    .items(List.of(CreateOrderItemRequest.builder()
                            .milkType(MilkType.FULL_CREAM).quantity(new BigDecimal("1.0")).build()))
                    .build();

            assertThatThrownBy(() -> service.createManualOrder(req, customerId))
                    .isInstanceOf(OrderException.class)
                    .hasMessageContaining("Could not verify milk production capacity");
            verify(orderRepository, never()).save(any());
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

    // ── FindBySubscription ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("findBySubscription()")
    class FindBySubscription {

        private final UUID subscriptionId = UUID.randomUUID();

        @Test
        @DisplayName("customer owns the subscription's orders → returns them")
        void customerOwns() {
            Order order = buildPendingOrder();
            var page = new org.springframework.data.domain.PageImpl<>(List.of(order));
            when(orderRepository.findAllBySubscriptionIdAndCustomerIdAndDeletedFalse(
                    eq(subscriptionId), eq(customerId), any())).thenReturn(page);
            when(orderMapper.toResponse(order)).thenReturn(buildResponse(OrderStatus.PENDING));

            Page<OrderResponse> result = service.findBySubscription(
                    subscriptionId, customerId, org.springframework.data.domain.Pageable.unpaged());

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(orderRepository, never()).findAllBySubscriptionIdAndDeletedFalse(any(), any());
        }

        @Test
        @DisplayName("subscription belongs to a different customer → empty page, not an error")
        void strangerGetsEmptyPage() {
            when(orderRepository.findAllBySubscriptionIdAndCustomerIdAndDeletedFalse(
                    eq(subscriptionId), eq(customerId), any()))
                    .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of()));

            Page<OrderResponse> result = service.findBySubscription(
                    subscriptionId, customerId, org.springframework.data.domain.Pageable.unpaged());

            assertThat(result.getTotalElements()).isZero();
        }

        @Test
        @DisplayName("admin (null customerId) → queries without customer filter")
        void adminAccess() {
            Order order = buildPendingOrder();
            var page = new org.springframework.data.domain.PageImpl<>(List.of(order));
            when(orderRepository.findAllBySubscriptionIdAndDeletedFalse(eq(subscriptionId), any())).thenReturn(page);
            when(orderMapper.toResponse(order)).thenReturn(buildResponse(OrderStatus.PENDING));

            service.findBySubscription(subscriptionId, null, org.springframework.data.domain.Pageable.unpaged());

            verify(orderRepository).findAllBySubscriptionIdAndDeletedFalse(eq(subscriptionId), any());
            verify(orderRepository, never()).findAllBySubscriptionIdAndCustomerIdAndDeletedFalse(any(), any(), any());
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

    // ── GetSummary ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getSummary()")
    class GetSummary {

        @Test
        @DisplayName("returns today's order count and pending order count from repository")
        void happyPath() {
            when(orderRepository.countByOrderDateAndDeletedFalse(LocalDate.now())).thenReturn(5L);
            when(orderRepository.countByStatusAndDeletedFalse(OrderStatus.PENDING)).thenReturn(3L);

            OrderSummaryResponse result = service.getSummary();

            assertThat(result.getTodaysOrders()).isEqualTo(5L);
            assertThat(result.getPendingOrders()).isEqualTo(3L);
        }
    }

    // ── GetReport (Sales Report) ────────────────────────────────────────────────

    @Nested
    @DisplayName("getReport()")
    class GetReport {

        @Test
        @DisplayName("maps the repository page into report rows and totals")
        void happyPath() {
            Order order = buildPendingOrder();
            var page = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
            when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(entityManager.createQuery(any(CriteriaQuery.class)).getSingleResult())
                    .thenReturn(new BigDecimal("120.00"));

            ReportPage<SalesReportRow, SalesReportSummary> result = service.getReport(
                    null, null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getOrderId()).isEqualTo(orderId);
            assertThat(result.getContent().get(0).getOrderNumber()).isEqualTo("ORD-2026-100001");
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getSummary().getTotalOrders()).isEqualTo(1);
            assertThat(result.getSummary().getTotalRevenue()).isEqualByComparingTo("120.00");
        }

        @Test
        @DisplayName("no matching orders → empty content with zeroed summary")
        void noResults() {
            var page = new PageImpl<Order>(List.of(), PageRequest.of(0, 20), 0);
            when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(entityManager.createQuery(any(CriteriaQuery.class)).getSingleResult())
                    .thenReturn(BigDecimal.ZERO);

            ReportPage<SalesReportRow, SalesReportSummary> result = service.getReport(
                    LocalDate.now().minusDays(7), LocalDate.now(), OrderStatus.CANCELLED, customerId, MilkType.TONED,
                    PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getSummary().getTotalOrders()).isZero();
            assertThat(result.getSummary().getTotalRevenue()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    // ── Search ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("search()")
    class Search {

        @Test
        @DisplayName("keyword + customer + status + date range all combine into one query")
        void allFiltersCombine() {
            Order order = buildPendingOrder();
            var page = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
            when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(orderMapper.toResponse(order)).thenReturn(buildResponse(OrderStatus.PENDING));

            Page<OrderResponse> result = service.search(customerId, "ORD-2026", LocalDate.now().minusDays(7),
                    LocalDate.now(), OrderStatus.PENDING, MilkType.FULL_CREAM, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getContent().get(0).getOrderNumber()).isEqualTo("ORD-2026-100001");
        }

        @Test
        @DisplayName("blank keyword and null customerId → no keyword/customer predicate applied")
        void noOptionalFilters() {
            var page = new PageImpl<Order>(List.of(), PageRequest.of(0, 20), 0);
            when(orderRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);

            Page<OrderResponse> result = service.search(null, "  ", null, null, null, null, PageRequest.of(0, 20));

            assertThat(result.getTotalElements()).isZero();
        }
    }

    // ── Export ───────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("export()")
    class Export {

        @Test
        @DisplayName("CSV format → streams matching orders as CSV rows")
        void csv_streamsMatchingRows() throws Exception {
            Order order = buildPendingOrder();
            var firstPage = new PageImpl<>(List.of(order),
                    PageRequest.of(0, 500, org.springframework.data.domain.Sort.by("orderDate").ascending()), 1);
            var emptyPage = new PageImpl<Order>(List.of());
            when(orderRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(firstPage, emptyPage);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.export(ExportFormat.CSV, out, customerId, "ORD-2026", null, null,
                    OrderStatus.PENDING, MilkType.FULL_CREAM, "orderDate", true);

            String content = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(content).contains("Order Number").contains("ORD-2026-100001").contains("PENDING");
        }

        @Test
        @DisplayName("no matching orders → writes header only, no rows")
        void noMatches_writesHeaderOnly() throws Exception {
            when(orderRepository.findAll(any(Specification.class), any(PageRequest.class)))
                    .thenReturn(new PageImpl<Order>(List.of()));

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            service.export(ExportFormat.CSV, out, null, null, null, null, null, null, "orderDate", false);

            String content = out.toString(java.nio.charset.StandardCharsets.UTF_8);
            assertThat(content.trim()).isEqualTo(
                    "Order Number,Customer ID,Order Date,Type,Status,Total Amount,Created At");
        }
    }
}
