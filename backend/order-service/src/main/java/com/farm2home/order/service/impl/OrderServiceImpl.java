package com.farm2home.order.service.impl;

import com.farm2home.order.config.MilkPriceProperties;
import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.entity.OrderItem;
import com.farm2home.order.domain.enums.OrderStatus;
import com.farm2home.order.domain.enums.OrderType;
import com.farm2home.order.domain.repository.OrderRepository;
import com.farm2home.order.dto.request.CreateOrderRequest;
import com.farm2home.order.dto.request.UpdateOrderStatusRequest;
import com.farm2home.order.dto.response.OrderResponse;
import com.farm2home.order.exception.OrderException;
import com.farm2home.order.exception.ResourceNotFoundException;
import com.farm2home.order.kafka.OrderEventProducer;
import com.farm2home.order.mapper.OrderMapper;
import com.farm2home.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final MilkPriceProperties priceProperties;
    private final OrderMapper orderMapper;
    private final OrderEventProducer eventProducer;

    @Override
    @Transactional
    public OrderResponse createManualOrder(CreateOrderRequest request, UUID customerId) {
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new OrderException("Order must have at least one item.");
        }

        String orderNumber = String.format("ORD-%d-%06d",
                request.getOrderDate().getYear(), orderRepository.nextOrderNumber());

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .customerId(customerId)
                .orderDate(request.getOrderDate())
                .orderType(OrderType.ONE_TIME)
                .status(OrderStatus.PENDING)
                .notes(request.getNotes())
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (var itemReq : request.getItems()) {
            BigDecimal unitPrice  = priceProperties.getPriceFor(itemReq.getMilkType().name());
            BigDecimal totalPrice = itemReq.getQuantity().multiply(unitPrice);
            total = total.add(totalPrice);

            OrderItem item = OrderItem.builder()
                    .milkType(itemReq.getMilkType())
                    .quantity(itemReq.getQuantity())
                    .unitPrice(unitPrice)
                    .totalPrice(totalPrice)
                    .build();
            order.addItem(item);
        }

        order.setTotalAmount(total);
        Order saved = orderRepository.save(order);
        eventProducer.publishOrderCreated(saved);
        log.info("Created manual order {} for customer {}", saved.getOrderNumber(), customerId);
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
                                      UUID customerId, boolean isAdmin) {
        Order order = findOrder(id, isAdmin ? null : customerId);
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
        return orderMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public void cancel(UUID id, UUID customerId, boolean isAdmin) {
        Order order = findOrder(id, isAdmin ? null : customerId);

        if (order.getStatus().isTerminal()) {
            throw new OrderException(
                    "Cannot cancel an order with status: " + order.getStatus().name());
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);
        log.info("Cancelled order {}", order.getOrderNumber());
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────

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
