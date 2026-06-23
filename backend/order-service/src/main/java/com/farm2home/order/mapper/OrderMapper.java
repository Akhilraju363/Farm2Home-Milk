package com.farm2home.order.mapper;

import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.entity.OrderItem;
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
}
