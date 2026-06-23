package com.farm2home.delivery.mapper;

import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.dto.response.AssignmentResponse;
import com.farm2home.delivery.dto.response.PartnerResponse;
import com.farm2home.delivery.dto.response.RouteResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

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
}
