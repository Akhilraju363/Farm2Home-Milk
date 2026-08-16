package com.farm2home.order.mapper;

import com.farm2home.order.domain.entity.Order;
import com.farm2home.order.domain.entity.OrderItem;
import com.farm2home.order.dto.request.CreateOrderItemRequest;
import com.farm2home.order.dto.response.OrderItemResponse;
import com.farm2home.order.dto.response.OrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {

    @Mapping(target = "orderType", expression = "java(order.getOrderType().name())")
    @Mapping(target = "status", expression = "java(order.getStatus().name())")
    OrderResponse toResponse(Order order);

    @Mapping(target = "milkType", expression = "java(item.getMilkType() != null ? item.getMilkType().name() : null)")
    OrderItemResponse toItemResponse(OrderItem item);

    // unitPrice/totalPrice/productName come from a price-list or inventory-service lookup done in
    // the service, never the request - productId maps straight through (auto-matched by field
    // name), since the client legitimately does choose which product it means.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "order", ignore = true)
    @Mapping(target = "productName", ignore = true)
    @Mapping(target = "unitPrice", ignore = true)
    @Mapping(target = "totalPrice", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    OrderItem toItemEntity(CreateOrderItemRequest itemRequest);
}
