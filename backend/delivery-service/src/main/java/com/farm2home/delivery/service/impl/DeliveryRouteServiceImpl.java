package com.farm2home.delivery.service.impl;

import com.farm2home.common.web.exception.ConflictException;
import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryRouteRepository;
import com.farm2home.delivery.domain.repository.DeliveryRouteSpecifications;
import com.farm2home.delivery.dto.request.CreateRouteRequest;
import com.farm2home.delivery.dto.request.UpdateRouteRequest;
import com.farm2home.delivery.dto.request.UpdateRouteStatusRequest;
import com.farm2home.delivery.dto.response.RouteResponse;
import com.farm2home.delivery.exception.ResourceNotFoundException;
import com.farm2home.delivery.mapper.DeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryRouteServiceImpl {

    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
            List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.OUT_FOR_DELIVERY);

    private final DeliveryRouteRepository routeRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryMapper mapper;

    @Transactional
    public RouteResponse create(CreateRouteRequest request) {
        // Route codes are always persisted upper-cased (see DeliveryMapper.toEntity below), so the
        // duplicate check must compare against the same upper-cased form - checking the raw
        // submitted casing let a differently-cased duplicate (e.g. "fr-01" when "FR-01" already
        // exists) slip past this check and hit the DB's case-sensitive unique constraint raw,
        // surfacing as an unhandled 500 instead of this 409.
        String normalizedCode = request.getRouteCode().toUpperCase();
        if (routeRepository.existsByRouteCodeAndDeletedFalse(normalizedCode)) {
            throw new ConflictException("Route code already exists: " + normalizedCode);
        }
        DeliveryRoute route = mapper.toEntity(request);
        return mapper.toRouteResponse(routeRepository.save(route));
    }

    @Transactional(readOnly = true)
    public Page<RouteResponse> findAll(Pageable pageable) {
        return routeRepository.findAllByDeletedFalse(pageable).map(mapper::toRouteResponse);
    }

    /** keyword matches routeName/routeCode/area/city/pincode; active null = both active and
     *  inactive routes (the admin Route Management table); active=true is how the Assign
     *  Delivery dialog gets an active-only route list without any client-side filtering. */
    @Transactional(readOnly = true)
    public Page<RouteResponse> search(String keyword, Boolean active, Pageable pageable) {
        Specification<DeliveryRoute> spec = Specification.where(DeliveryRouteSpecifications.notDeleted());
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(DeliveryRouteSpecifications.hasKeyword(keyword));
        }
        if (active != null) {
            spec = spec.and(DeliveryRouteSpecifications.isActive(active));
        }
        return routeRepository.findAll(spec, pageable).map(mapper::toRouteResponse);
    }

    @Transactional(readOnly = true)
    public RouteResponse findById(UUID id) {
        return mapper.toRouteResponse(routeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id)));
    }

    @Transactional
    public RouteResponse update(UUID id, UpdateRouteRequest request) {
        DeliveryRoute route = routeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        mapper.updateRouteFromRequest(request, route);
        return mapper.toRouteResponse(routeRepository.save(route));
    }

    @Transactional
    public RouteResponse updateStatus(UUID id, UpdateRouteStatusRequest request) {
        DeliveryRoute route = routeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        route.setActive(request.getActive());
        return mapper.toRouteResponse(routeRepository.save(route));
    }

    @Transactional
    public void delete(UUID id) {
        DeliveryRoute route = routeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        if (assignmentRepository.existsByRoute_IdAndStatusIn(id, ACTIVE_ASSIGNMENT_STATUSES)) {
            throw new ConflictException(
                    "This route cannot be deleted because it is currently assigned to active deliveries.");
        }
        route.setDeleted(true);
        routeRepository.save(route);
    }
}
