package com.farm2home.subscription.mapper;

import com.farm2home.subscription.domain.entity.Subscription;
import com.farm2home.subscription.domain.enums.DeliveryDay;
import com.farm2home.subscription.dto.request.CreateSubscriptionRequest;
import com.farm2home.subscription.dto.request.UpdateSubscriptionRequest;
import com.farm2home.subscription.dto.response.SubscriptionResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.Collections;
import java.util.List;

@Mapper(componentModel = "spring")
public interface SubscriptionMapper {

    @Mapping(target = "milkType", expression = "java(subscription.getMilkType().name())")
    @Mapping(target = "scheduleType", expression = "java(subscription.getScheduleType().name())")
    @Mapping(target = "status", expression = "java(subscription.getStatus().name())")
    @Mapping(target = "deliveryDays", expression = "java(toStringList(subscription.getDeliveryDays()))")
    SubscriptionResponse toResponse(Subscription subscription);

    // customerId comes from the resolved principal/param in the service, not the request body;
    // status/pauseStart/pauseEnd/audit fields are all assigned or defaulted by the service.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "pauseStart", ignore = true)
    @Mapping(target = "pauseEnd", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Subscription toEntity(CreateSubscriptionRequest request);

    // Partial update: only non-null request fields overwrite the entity. Validation that
    // depends on the *old* entity state (e.g. end date vs. current start date, WEEKLY-schedule
    // delivery-day requirements) stays in the service, which runs it before calling this.
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "startDate", ignore = true)
    @Mapping(target = "pauseStart", ignore = true)
    @Mapping(target = "pauseEnd", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateEntityFromRequest(UpdateSubscriptionRequest request, @MappingTarget Subscription entity);

    default List<String> toStringList(List<DeliveryDay> days) {
        if (days == null || days.isEmpty()) return Collections.emptyList();
        return days.stream().map(Enum::name).toList();
    }
}
