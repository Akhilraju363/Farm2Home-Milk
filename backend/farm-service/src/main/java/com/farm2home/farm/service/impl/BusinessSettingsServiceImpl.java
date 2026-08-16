package com.farm2home.farm.service.impl;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.Audited;
import com.farm2home.farm.domain.entity.BusinessSettings;
import com.farm2home.farm.domain.repository.BusinessSettingsRepository;
import com.farm2home.farm.dto.request.UpdateBusinessSettingsRequest;
import com.farm2home.farm.dto.response.BusinessSettingsResponse;
import com.farm2home.farm.exception.ResourceNotFoundException;
import com.farm2home.farm.mapper.FarmMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Singleton row (id=1, seeded by V6__create_business_settings.sql) holding Farm2Home's own
 * delivery origin + radius - see BusinessSettings' own javadoc for why this is separate from
 * the multi-row Farm (supplier registry) entity.
 */
@Service
@RequiredArgsConstructor
public class BusinessSettingsServiceImpl {

    private static final short SINGLETON_ID = 1;

    private final BusinessSettingsRepository repository;
    private final FarmMapper mapper;

    @Transactional(readOnly = true)
    public BusinessSettingsResponse get() {
        return mapper.toBusinessSettingsResponse(getSettings());
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "BusinessSettings")
    public BusinessSettingsResponse update(UpdateBusinessSettingsRequest request) {
        BusinessSettings settings = getSettings();
        mapper.updateBusinessSettingsFromRequest(request, settings);
        return mapper.toBusinessSettingsResponse(repository.save(settings));
    }

    private BusinessSettings getSettings() {
        return repository.findById(SINGLETON_ID)
                .orElseThrow(() -> new ResourceNotFoundException("Business settings are not configured"));
    }
}
