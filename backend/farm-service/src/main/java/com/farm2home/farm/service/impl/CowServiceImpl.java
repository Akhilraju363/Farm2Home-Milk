package com.farm2home.farm.service.impl;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.Audited;
import com.farm2home.farm.domain.entity.Cow;
import com.farm2home.farm.domain.enums.CowStatus;
import com.farm2home.farm.domain.repository.CowRepository;
import com.farm2home.farm.dto.request.CreateCowRequest;
import com.farm2home.farm.dto.request.UpdateCowRequest;
import com.farm2home.farm.dto.request.UpdateCowStatusRequest;
import com.farm2home.farm.dto.response.CowResponse;
import com.farm2home.farm.exception.FarmException;
import com.farm2home.farm.exception.ResourceNotFoundException;
import com.farm2home.farm.mapper.FarmMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CowServiceImpl {

    private final CowRepository cowRepository;
    private final FarmMapper mapper;

    @Transactional
    @Audited(action = AuditAction.CREATE, entityType = "Cow")
    public CowResponse create(CreateCowRequest request) {
        if (cowRepository.existsByTagNumberAndDeletedFalse(request.getTagNumber())) {
            throw new FarmException("Tag number already registered: " + request.getTagNumber());
        }
        Cow cow = mapper.toEntity(request);
        return mapper.toCowResponse(cowRepository.save(cow));
    }

    @Transactional(readOnly = true)
    public Page<CowResponse> findAll(CowStatus statusFilter, UUID farmId, Pageable pageable) {
        if (farmId != null && statusFilter != null) {
            return cowRepository.findAllByFarmIdAndStatusAndDeletedFalse(farmId, statusFilter, pageable)
                    .map(mapper::toCowResponse);
        }
        if (farmId != null) {
            return cowRepository.findAllByFarmIdAndDeletedFalse(farmId, pageable).map(mapper::toCowResponse);
        }
        if (statusFilter != null) {
            return cowRepository.findAllByStatusAndDeletedFalse(statusFilter, pageable).map(mapper::toCowResponse);
        }
        return cowRepository.findAllByDeletedFalse(pageable).map(mapper::toCowResponse);
    }

    /** Id-only lookup for cross-service filter resolution (e.g. reports-service resolving a
     *  Farm filter into cow ids before querying production-service). */
    @Transactional(readOnly = true)
    public List<UUID> findIdsByFarm(UUID farmId) {
        return cowRepository.findIdsByFarmIdAndDeletedFalse(farmId);
    }

    @Transactional(readOnly = true)
    public CowResponse findById(UUID id) {
        return mapper.toCowResponse(getCow(id));
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "Cow")
    public CowResponse update(UUID id, UpdateCowRequest request) {
        Cow cow = getCow(id);
        mapper.updateCowFromRequest(request, cow);
        return mapper.toCowResponse(cowRepository.save(cow));
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "Cow")
    public CowResponse updateStatus(UUID id, UpdateCowStatusRequest request) {
        Cow cow = getCow(id);
        if (!cow.getStatus().canTransitionTo(request.getStatus())) {
            throw new FarmException(
                    "Cannot transition cow from " + cow.getStatus().name() + " to " + request.getStatus().name() + ".");
        }
        cow.setStatus(request.getStatus());
        return mapper.toCowResponse(cowRepository.save(cow));
    }

    @Transactional
    @Audited(action = AuditAction.DELETE, entityType = "Cow")
    public void delete(UUID id) {
        Cow cow = getCow(id);
        cow.setDeleted(true);
        cowRepository.save(cow);
    }

    private Cow getCow(UUID id) {
        return cowRepository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Cow not found: " + id));
    }
}
