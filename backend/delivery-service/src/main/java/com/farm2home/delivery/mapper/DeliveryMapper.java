package com.farm2home.delivery.mapper;

import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.dto.request.CreatePartnerRequest;
import com.farm2home.delivery.dto.request.CreateRouteRequest;
import com.farm2home.delivery.dto.request.UpdatePartnerRequest;
import com.farm2home.delivery.dto.request.UpdateRouteRequest;
import com.farm2home.delivery.dto.response.AssignmentResponse;
import com.farm2home.delivery.dto.response.PartnerResponse;
import com.farm2home.delivery.dto.response.RouteResponse;
import org.mapstruct.BeanMapping;
import org.mapstruct.Condition;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.springframework.util.StringUtils;

@Mapper(componentModel = "spring")
public interface DeliveryMapper {

    RouteResponse toRouteResponse(DeliveryRoute route);

    @Mapping(target = "routeId",   expression = "java(partner.getRoute() != null ? partner.getRoute().getId() : null)")
    @Mapping(target = "routeCode", expression = "java(partner.getRoute() != null ? partner.getRoute().getRouteCode() : null)")
    PartnerResponse toPartnerResponse(DeliveryPartner partner);

    @Mapping(target = "deliveryPartnerId",     expression = "java(assignment.getDeliveryPartner().getId())")
    @Mapping(target = "deliveryPartnerName",   expression = "java(assignment.getDeliveryPartner().getName())")
    @Mapping(target = "deliveryPartnerMobile", expression = "java(assignment.getDeliveryPartner().getMobile())")
    @Mapping(target = "routeId",   expression = "java(assignment.getRoute().getId())")
    @Mapping(target = "routeCode", expression = "java(assignment.getRoute().getRouteCode())")
    @Mapping(target = "status",    expression = "java(assignment.getStatus().name())")
    AssignmentResponse toAssignmentResponse(DeliveryAssignment assignment);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "routeCode", expression = "java(request.getRouteCode().toUpperCase())")
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    DeliveryRoute toEntity(CreateRouteRequest request);

    // hasText() below means blank strings are skipped the same as nulls, matching the
    // StringUtils.hasText guards this replaces - a plain null-check isn't enough since a
    // caller could send an empty string to mean "leave unchanged".
    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updateRouteFromRequest(UpdateRouteRequest request, @MappingTarget DeliveryRoute route);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "route", ignore = true)
    @Mapping(target = "active", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    DeliveryPartner toEntity(CreatePartnerRequest request);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "route", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    void updatePartnerFromRequest(UpdatePartnerRequest request, @MappingTarget DeliveryPartner partner);

    @Condition
    default boolean hasText(String value) {
        return StringUtils.hasText(value);
    }
}
