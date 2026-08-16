package com.farm2home.customer.service.impl;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.Audited;
import com.farm2home.customer.domain.entity.DataRightsRequest;
import com.farm2home.customer.domain.repository.DataRightsRequestRepository;
import com.farm2home.customer.dto.request.CreateDataRightsRequestRequest;
import com.farm2home.customer.dto.request.UpdateDataRightsRequestStatusRequest;
import com.farm2home.customer.dto.response.DataRightsRequestResponse;
import com.farm2home.customer.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DataRightsRequestServiceImpl {

    private final DataRightsRequestRepository repository;

    @Transactional
    @Audited(action = AuditAction.CREATE, entityType = "DataRightsRequest")
    public DataRightsRequestResponse create(CreateDataRightsRequestRequest request, UUID customerId) {
        DataRightsRequest entity = DataRightsRequest.builder()
                .customerId(customerId)
                .requesterName(request.getRequesterName())
                .requesterContact(request.getRequesterContact())
                .requestType(request.getRequestType())
                .details(request.getDetails())
                .build();
        return toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<DataRightsRequestResponse> findAll(Pageable pageable) {
        return repository.findAllByOrderByCreatedAtDesc(pageable).map(this::toResponse);
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "DataRightsRequest")
    public DataRightsRequestResponse updateStatus(UUID id, UpdateDataRightsRequestStatusRequest request) {
        DataRightsRequest entity = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Data rights request not found: " + id));
        entity.setStatus(request.getStatus());
        entity.setResolutionNotes(request.getResolutionNotes());
        return toResponse(repository.save(entity));
    }

    private DataRightsRequestResponse toResponse(DataRightsRequest r) {
        return DataRightsRequestResponse.builder()
                .id(r.getId())
                .customerId(r.getCustomerId())
                .requesterName(r.getRequesterName())
                .requesterContact(r.getRequesterContact())
                .requestType(r.getRequestType())
                .details(r.getDetails())
                .status(r.getStatus())
                .resolutionNotes(r.getResolutionNotes())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
