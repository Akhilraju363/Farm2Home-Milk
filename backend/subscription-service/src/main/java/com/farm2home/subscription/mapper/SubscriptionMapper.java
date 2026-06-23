package com.farm2home.subscription.mapper;

import com.farm2home.subscription.domain.entity.Subscription;
import com.farm2home.subscription.domain.enums.DeliveryDay;
import com.farm2home.subscription.dto.response.SubscriptionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(target = "milkType", expression = "java(subscription.getMilkType().name())")
    @Mapping(target = "scheduleType", expression = "java(subscription.getScheduleType().name())")
    @Mapping(target = "status", expression = "java(subscription.getStatus().name())")
    @Mapping(target = "deliveryDays", expression = "java(toStringList(subscription.getDeliveryDays()))")
    SubscriptionResponse toResponse(Subscription subscription);

    default List<String> toStringList(List<DeliveryDay> days) {
        if (days == null || days.isEmpty()) return Collections.emptyList();
        return days.stream().map(Enum::name).toList();
    }
}
