package com.farm2home.order.service.impl;

import com.farm2home.order.client.DailyProductionResponse;
import com.farm2home.order.client.ProductionServiceClient;
import com.farm2home.order.config.MilkPriceProperties;
import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.entity.OrderItem;
import com.farm2home.order.domain.enums.MilkType;
import com.farm2home.order.domain.enums.OrderStatus;
import com.farm2home.order.domain.enums.OrderType;
import com.farm2home.order.domain.repository.OrderRepository;
import com.farm2home.order.domain.repository.OrderSpecifications;
import com.farm2home.order.dto.request.CreateOrderRequest;
import com.farm2home.order.dto.request.UpdateOrderStatusRequest;
import com.farm2home.order.dto.response.OrderResponse;
import com.farm2home.order.exception.OrderException;
import com.farm2home.order.exception.ResourceNotFoundException;
import com.farm2home.order.kafka.OrderEventProducer;
import com.farm2home.order.mapper.OrderMapper;
import com.farm2home.order.service.OrderService;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.dashboard.OrderSummaryResponse;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.core.reports.SalesReportRow;
import com.farm2home.common.core.reports.SalesReportSummary;
import com.farm2home.common.export.BatchSupplier;
import com.farm2home.common.export.ExportColumn;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.export.TabularExporterFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClientException;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final MilkPriceProperties priceProperties;
    private final OrderMapper orderMapper;
    private final OrderEventProducer eventProducer;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;
    private final ProductionServiceClient productionServiceClient;

    @Override
    @Transactional
    public OrderResponse createManualOrder(CreateOrderRequest request, UUID customerId) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new OrderException("Order must have at least one item.");
        }

        BigDecimal requestedQuantity = request.getItems().stream()
                .map(com.farm2home.order.dto.request.CreateOrderItemRequest::getQuantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        verifyProductionCapacity(request.getOrderDate(), requestedQuantity);

        String orderNumber = String.format("ORD-%d-%06d",
                request.getOrderDate().getYear(), orderRepository.nextOrderNumber());

        Order order = orderMapper.toEntity(request);
        order.setOrderNumber(orderNumber);
        order.setCustomerId(customerId);
        order.setOrderType(OrderType.ONE_TIME);
        order.setStatus(OrderStatus.PENDING);

        BigDecimal total = BigDecimal.ZERO;
        for (var itemReq : request.getItems()) {
            BigDecimal unitPrice  = priceProperties.getPriceFor(itemReq.getMilkType().name());
            BigDecimal totalPrice = itemReq.getQuantity().multiply(unitPrice);
            total = total.add(totalPrice);

            OrderItem item = orderMapper.toItemEntity(itemReq);
            item.setUnitPrice(unitPrice);
            item.setTotalPrice(totalPrice);
            order.addItem(item);
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        eventProducer.publishOrderCreated(saved);
        log.info("Created manual order {} for customer {}", saved.getOrderNumber(), customerId);
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.CREATE)
                .entityType("Order")
                .entityId(saved.getId().toString())
                .userId(customerId.toString())
                .details("Order " + saved.getOrderNumber() + " created")
                .build());
        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findAll(UUID customerId, Pageable pageable) {
        if (customerId == null) {
            return orderRepository.findAllByDeletedFalse(pageable).map(orderMapper::toResponse);
        }
        return orderRepository.findAllByCustomerIdAndDeletedFalse(customerId, pageable)
                .map(orderMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse findById(UUID id, UUID customerId) {
        return orderMapper.toResponse(findOrder(id, customerId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> findBySubscription(UUID subscriptionId, Pageable pageable) {
        return orderRepository.findAllBySubscriptionIdAndDeletedFalse(subscriptionId, pageable)
                .map(orderMapper::toResponse);
    }

    @Override
    @Transactional
    public OrderResponse updateStatus(UUID id, UpdateOrderStatusRequest request,
                                      UUID customerId, boolean isAdmin, UUID actorId) {
        Order order = findOrder(id, isAdmin ? null : customerId);
        OrderStatus previousStatus = order.getStatus();
        OrderStatus newStatus = request.getStatus();

        if (!order.getStatus().canTransitionTo(newStatus)) {
            throw new OrderException(String.format(
                    "Invalid status transition: %s → %s",
                    order.getStatus().name(), newStatus.name()));
        }

        order.setStatus(newStatus);
        if (request.getNotes() != null) {
            order.setNotes(request.getNotes());
        }

        Order saved = orderRepository.save(order);
        log.info("Order {} transitioned to {}", order.getOrderNumber(), newStatus);
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.UPDATE)
                .entityType("Order")
                .entityId(saved.getId().toString())
                .userId(actorId != null ? actorId.toString() : null)
                .oldValue(previousStatus.name())
                .newValue(newStatus.name())
                .details("Order " + saved.getOrderNumber() + " status: " + previousStatus + " -> " + newStatus)
                .build());
        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void cancel(UUID id, UUID customerId, boolean isAdmin, UUID actorId) {
        Order order = findOrder(id, isAdmin ? null : customerId);
        OrderStatus previousStatus = order.getStatus();

        if (order.getStatus().isTerminal()) {
            throw new OrderException(
                    "Cannot cancel an order with status: " + order.getStatus().name());
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Cancelled order {}", order.getOrderNumber());
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.UPDATE)
                .entityType("Order")
                .entityId(order.getId().toString())
                .userId(actorId != null ? actorId.toString() : null)
                .oldValue(previousStatus.name())
                .newValue(OrderStatus.CANCELLED.name())
                .details("Order " + order.getOrderNumber() + " cancelled (was " + previousStatus + ")")
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public OrderSummaryResponse getSummary() {
        LocalDate today = LocalDate.now();
        return OrderSummaryResponse.builder()
                .todaysOrders(orderRepository.countByOrderDateAndDeletedFalse(today))
                .pendingOrders(orderRepository.countByStatusAndDeletedFalse(OrderStatus.PENDING))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReportPage<SalesReportRow, SalesReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            OrderStatus status, UUID customerId, MilkType milkType, Pageable pageable) {
        Specification<Order> spec = Specification.where(OrderSpecifications.notDeleted());
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(OrderSpecifications.dateBetween(dateFrom, dateTo));
        }
        if (status != null) {
            spec = spec.and(OrderSpecifications.hasStatus(status));
        }
        if (customerId != null) {
            spec = spec.and(OrderSpecifications.hasCustomer(customerId));
        }
        if (milkType != null) {
            spec = spec.and(OrderSpecifications.hasMilkType(milkType));
        }

        Page<Order> page = orderRepository.findAll(spec, pageable);

        List<SalesReportRow> rows = page.getContent().stream()
                .map(o -> SalesReportRow.builder()
                        .orderId(o.getId())
                        .orderNumber(o.getOrderNumber())
                        .customerId(o.getCustomerId())
                        .orderDate(o.getOrderDate())
                        .status(o.getStatus().name())
                        .totalAmount(o.getTotalAmount())
                        .build())
                .toList();

        SalesReportSummary summary = SalesReportSummary.builder()
                .totalOrders(page.getTotalElements())
                .totalRevenue(sumTotalAmount(spec))
                .build();

        return ReportPage.<SalesReportRow, SalesReportSummary>builder()
                .content(rows)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .summary(summary)
                .build();
    }

    /** Reuses the same Specification that builds the page's WHERE clause, so the aggregate
     *  total is always computed over exactly the same filtered set - just a different SELECT. */
    private BigDecimal sumTotalAmount(Specification<Order> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> cq = cb.createQuery(BigDecimal.class);
        Root<Order> root = cq.from(Order.class);
        cq.select(cb.coalesce(cb.sum(root.get("totalAmount")), BigDecimal.ZERO));
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        return entityManager.createQuery(cq).getSingleResult();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> search(UUID customerId, String keyword, LocalDate dateFrom, LocalDate dateTo,
            OrderStatus status, MilkType milkType, Pageable pageable) {
        Specification<Order> spec = buildSearchSpecification(customerId, keyword, dateFrom, dateTo, status, milkType);
        return orderRepository.findAll(spec, pageable).map(orderMapper::toResponse);
    }

    /** Shared by both {@link #search} and {@link #export} so the two always see the exact same
     *  filtered result set. */
    private Specification<Order> buildSearchSpecification(UUID customerId, String keyword, LocalDate dateFrom,
            LocalDate dateTo, OrderStatus status, MilkType milkType) {
        Specification<Order> spec = Specification.where(OrderSpecifications.notDeleted());
        if (customerId != null) {
            spec = spec.and(OrderSpecifications.hasCustomer(customerId));
        }
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(OrderSpecifications.hasKeyword(keyword));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(OrderSpecifications.dateBetween(dateFrom, dateTo));
        }
        if (status != null) {
            spec = spec.and(OrderSpecifications.hasStatus(status));
        }
        if (milkType != null) {
            spec = spec.and(OrderSpecifications.hasMilkType(milkType));
        }
        return spec;
    }

    /** Streams matching orders straight to {@code out} in the requested file format, one
     *  bounded page at a time, so exporting a very large order history never requires holding
     *  the full result set in memory. Each batch fetch runs in its own short-lived Spring Data
     *  transaction (this method is deliberately NOT wrapped in a single @Transactional so a
     *  slow export doesn't pin one DB connection for its entire duration). Runs on the async
     *  StreamingResponseBody dispatch thread, not the original request thread. */
    @Override
    public void export(ExportFormat format, OutputStream out, UUID customerId, String keyword, LocalDate dateFrom,
            LocalDate dateTo, OrderStatus status, MilkType milkType, String sortBy, boolean ascending)
            throws IOException {
        Specification<Order> spec = buildSearchSpecification(customerId, keyword, dateFrom, dateTo, status, milkType);
        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        List<ExportColumn<Order>> columns = List.of(
                new ExportColumn<>("Order Number", Order::getOrderNumber),
                new ExportColumn<>("Customer ID", o -> o.getCustomerId().toString()),
                new ExportColumn<>("Order Date", o -> o.getOrderDate().toString()),
                new ExportColumn<>("Type", o -> o.getOrderType().name()),
                new ExportColumn<>("Status", o -> o.getStatus().name()),
                new ExportColumn<>("Total Amount", o -> o.getTotalAmount().toString()),
                new ExportColumn<>("Created At", o -> o.getCreatedAt() == null ? "" : o.getCreatedAt().toString()));

        BatchSupplier<Order> supplier = (page, size) ->
                orderRepository.findAll(spec, PageRequest.of(page, size, sort)).getContent();

        TabularExporterFactory.<Order>forFormat(format)
                .write(out, columns.stream().map(ExportColumn::header).toList(), columns, supplier);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

    /** Validate stock before creating an order: a same-day order can never push the day's
     *  cumulative ordered quantity past what production-service has actually recorded for that
     *  date. Deliberately scoped to same-day orders only - production is logged AFTER milk is
     *  collected, so a future-dated order (the normal case for subscriptions and advance manual
     *  orders) would always have zero recorded production yet; enforcing this check against a
     *  future date would incorrectly reject every legitimate advance order. */
    private void verifyProductionCapacity(LocalDate orderDate, BigDecimal requestedQuantity) {
        if (!orderDate.equals(LocalDate.now())) {
            return;
        }

        BigDecimal produced = fetchProducedLitersFor(orderDate);
        BigDecimal alreadyOrdered = orderRepository.sumOrderedQuantityByDate(orderDate);
        BigDecimal totalAfterThisOrder = alreadyOrdered.add(requestedQuantity);

        if (totalAfterThisOrder.compareTo(produced) > 0) {
            throw new OrderException(String.format(
                    "Insufficient milk production for %s: requesting %s L (%s L already ordered today) exceeds "
                            + "the %s L produced today.",
                    orderDate, requestedQuantity, alreadyOrdered, produced));
        }
    }

    private BigDecimal fetchProducedLitersFor(LocalDate date) {
        List<DailyProductionResponse> summaries;
        try {
            summaries = productionServiceClient.getDailySummary(date, date).block();
        } catch (WebClientException ex) {
            throw new OrderException("Could not verify milk production capacity right now. Please try again.");
        }
        if (summaries == null) {
            return BigDecimal.ZERO;
        }
        return summaries.stream()
                .map(DailyProductionResponse::getTotalLiters)
                .filter(java.util.Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private Order findOrder(UUID id, UUID customerId) {
        if (customerId == null) {
            return orderRepository.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Order not found: " + id));
        }
        return orderRepository.findByIdAndCustomerIdAndDeletedFalse(id, customerId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Order not found or access denied: " + id));
    }
}
