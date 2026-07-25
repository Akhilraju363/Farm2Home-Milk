package com.farm2home.order.mapper;

import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.entity.OrderItem;
import com.farm2home.order.dto.request.CreateOrderItemRequest;
import com.farm2home.order.dto.request.CreateOrderRequest;
import com.farm2home.order.dto.response.OrderItemResponse;
import com.farm2home.order.dto.response.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "orderType", expression = "java(order.getOrderType().name())")
    @Mapping(target = "status", expression = "java(order.getStatus().name())")
    OrderResponse toResponse(Order order);

    @Mapping(target = "milkType", expression = "java(item.getMilkType().name())")
    OrderItemResponse toItemResponse(OrderItem item);

    // orderNumber/customerId/orderType/status are assigned by the service (computed order
    // number, resolved customer, fixed ONE_TIME/PENDING for a manual order); items are built
    // and linked item-by-item in the service because each needs a priced lookup.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderNumber", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "subscriptionId", ignore = true)
    @Mapping(target = "orderType", ignore = true)
    @Mapping(target = "totalAmount", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "items", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Order toEntity(CreateOrderRequest request);

    // unitPrice/totalPrice come from a price-list lookup done in the service, not the request.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "unitPrice", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    OrderItem toItemEntity(CreateOrderItemRequest itemRequest);
}
