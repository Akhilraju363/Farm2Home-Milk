package com.farm2home.delivery.service.impl;

import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import com.farm2home.delivery.domain.repository.DeliveryRouteRepository;
import com.farm2home.delivery.dto.request.CreatePartnerRequest;
import com.farm2home.delivery.dto.request.UpdatePartnerRequest;
import com.farm2home.delivery.dto.response.PartnerResponse;
import com.farm2home.delivery.exception.ResourceNotFoundException;
import com.farm2home.delivery.mapper.DeliveryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeliveryPartnerServiceImpl {

    private final DeliveryPartnerRepository partnerRepository;
    private final DeliveryRouteRepository routeRepository;
    private final DeliveryMapper mapper;

    @Transactional
    public PartnerResponse create(CreatePartnerRequest request) {
        DeliveryRoute route = null;
        if (request.getRouteId() != null) {
            route = routeRepository.findByIdAndDeletedFalse(request.getRouteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + request.getRouteId()));
        }
        DeliveryPartner partner = mapper.toEntity(request);
        partner.setRoute(route);
        return mapper.toPartnerResponse(partnerRepository.save(partner));
    }

    @Transactional(readOnly = true)
    public Page<PartnerResponse> findAll(Pageable pageable) {
        return partnerRepository.findAllByDeletedFalse(pageable).map(mapper::toPartnerResponse);
    }

    @Transactional(readOnly = true)
    public PartnerResponse findById(UUID id) {
        return mapper.toPartnerResponse(partnerRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partner not found: " + id)));
    }

    @Transactional
    public PartnerResponse update(UUID id, UpdatePartnerRequest request) {
        DeliveryPartner partner = partnerRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Partner not found: " + id));

        if (request.getRouteId() != null) {
            DeliveryRoute route = routeRepository.findByIdAndDeletedFalse(request.getRouteId())
                    .orElseThrow(() -> new ResourceNotFoundException("Route not found: " + request.getRouteId()));
            partner.setRoute(route);
        }
        mapper.updatePartnerFromRequest(request, partner);

        return mapper.toPartnerResponse(partnerRepository.save(partner));
    }
}
