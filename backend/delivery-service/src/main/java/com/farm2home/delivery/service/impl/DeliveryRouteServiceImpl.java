package com.farm2home.delivery.service.impl;

import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.domain.repository.DeliveryRouteRepository;
import com.farm2home.delivery.dto.request.CreateRouteRequest;
import com.farm2home.delivery.dto.request.UpdateRouteRequest;
import com.farm2home.delivery.dto.response.RouteResponse;
import com.farm2home.delivery.exception.DeliveryException;
import com.farm2home.delivery.exception.ResourceNotFoundException;
import com.farm2home.delivery.mapper.DeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryRouteServiceImpl {

    private final DeliveryRouteRepository routeRepository;
    private final DeliveryMapper mapper;

    @Transactional
    public RouteResponse create(CreateRouteRequest request) {
        if (routeRepository.existsByRouteCodeAndDeletedFalse(request.getRouteCode())) {
            throw new DeliveryException("Route code already exists: " + request.getRouteCode());
        }
        DeliveryRoute route = DeliveryRoute.builder()
                .routeName(request.getRouteName())
                .routeCode(request.getRouteCode().toUpperCase())
                .area(request.getArea())
                .city(request.getCity())
                .pincode(request.getPincode())
                .build();
        return mapper.toRouteResponse(routeRepository.save(route));
    }

    @Transactional(readOnly = true)
    public Page<RouteResponse> findAll(Pageable pageable) {
        return routeRepository.findAllByDeletedFalse(pageable).map(mapper::toRouteResponse);
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
        if (StringUtils.hasText(request.getRouteName())) route.setRouteName(request.getRouteName());
        if (StringUtils.hasText(request.getArea()))      route.setArea(request.getArea());
        if (StringUtils.hasText(request.getCity()))      route.setCity(request.getCity());
        if (StringUtils.hasText(request.getPincode()))   route.setPincode(request.getPincode());
        if (request.getActive() != null)                  route.setActive(request.getActive());
        return mapper.toRouteResponse(routeRepository.save(route));
    }

    @Transactional
    public void delete(UUID id) {
        DeliveryRoute route = routeRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + id));
        route.setDeleted(true);
        routeRepository.save(route);
    }
}
