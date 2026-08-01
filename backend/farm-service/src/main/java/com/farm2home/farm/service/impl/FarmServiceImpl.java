package com.farm2home.farm.service.impl;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.Audited;
import com.farm2home.common.core.constants.FileConstants;
import com.farm2home.common.web.storage.FileStorageService;
import com.farm2home.farm.domain.entity.Farm;
import com.farm2home.farm.domain.repository.FarmRepository;
import com.farm2home.farm.domain.repository.FarmSpecifications;
import com.farm2home.farm.dto.request.CreateFarmRequest;
import com.farm2home.farm.dto.request.UpdateFarmRequest;
import com.farm2home.farm.dto.response.FarmResponse;
import com.farm2home.farm.exception.ResourceNotFoundException;
import com.farm2home.farm.mapper.FarmMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class FarmServiceImpl {

    private static final String UPLOAD_CATEGORY = FileConstants.CATEGORY_FARMS;

    private final FarmRepository repository;
    private final FarmMapper mapper;
    private final FileStorageService fileStorageService;

    @Transactional
    @Audited(action = AuditAction.CREATE, entityType = "Farm")
    public FarmResponse create(CreateFarmRequest request) {
        Farm farm = mapper.toEntity(request);
        return mapper.toFarmResponse(repository.save(farm));
    }

    @Transactional(readOnly = true)
    public Page<FarmResponse> findAll(Pageable pageable) {
        return repository.findAllByDeletedFalse(pageable).map(mapper::toFarmResponse);
    }

    @Transactional(readOnly = true)
    public FarmResponse findById(UUID id) {
        return mapper.toFarmResponse(getFarm(id));
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "Farm")
    public FarmResponse update(UUID id, UpdateFarmRequest request) {
        Farm farm = getFarm(id);
        mapper.updateFarmFromRequest(request, farm);
        return mapper.toFarmResponse(repository.save(farm));
    }

    @Transactional
    @Audited(action = AuditAction.DELETE, entityType = "Farm")
    public void delete(UUID id) {
        Farm farm = getFarm(id);
        farm.setDeleted(true);
        repository.save(farm);
    }

    @Transactional
    @Audited(action = AuditAction.UPLOAD, entityType = "Farm")
    public FarmResponse uploadImage(UUID id, MultipartFile file) {
        Farm farm = getFarm(id);
        String relativePath = fileStorageService.store(file, UPLOAD_CATEGORY);
        farm.setImageUrl("/uploads/" + relativePath);
        return mapper.toFarmResponse(repository.save(farm));
    }

    private Farm getFarm(UUID id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Farm not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<FarmResponse> search(String keyword, LocalDate dateFrom, LocalDate dateTo, Pageable pageable) {
        Specification<Farm> spec = Specification.where(FarmSpecifications.notDeleted());
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(FarmSpecifications.hasKeyword(keyword));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(FarmSpecifications.createdBetween(dateFrom, dateTo));
        }
        return repository.findAll(spec, pageable).map(mapper::toFarmResponse);
    }
}
