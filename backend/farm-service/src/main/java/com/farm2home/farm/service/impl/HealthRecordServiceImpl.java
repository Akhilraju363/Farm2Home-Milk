package com.farm2home.farm.service.impl;

import com.farm2home.farm.domain.entity.Cow;
import com.farm2home.farm.domain.entity.HealthRecord;
import com.farm2home.farm.domain.repository.CowRepository;
import com.farm2home.farm.domain.repository.HealthRecordRepository;
import com.farm2home.farm.dto.request.CreateHealthRecordRequest;
import com.farm2home.farm.dto.response.HealthRecordResponse;
import com.farm2home.farm.exception.ResourceNotFoundException;
import com.farm2home.farm.mapper.FarmMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HealthRecordServiceImpl {

    private final HealthRecordRepository healthRecordRepository;
    private final CowRepository cowRepository;
    private final FarmMapper mapper;

    @Transactional
    public HealthRecordResponse create(UUID cowId, CreateHealthRecordRequest request) {
        Cow cow = getCow(cowId);
        HealthRecord record = HealthRecord.builder()
                .cow(cow)
                .recordDate(request.getRecordDate())
                .condition(request.getCondition())
                .symptoms(request.getSymptoms())
                .treatment(request.getTreatment())
                .vetName(request.getVetName())
                .build();
        return mapper.toHealthRecordResponse(healthRecordRepository.save(record));
    }

    @Transactional(readOnly = true)
    public Page<HealthRecordResponse> findByCow(UUID cowId, Pageable pageable) {
        getCow(cowId);
        return healthRecordRepository.findAllByCowIdAndDeletedFalseOrderByRecordDateDesc(cowId, pageable)
                .map(mapper::toHealthRecordResponse);
    }

    @Transactional(readOnly = true)
    public HealthRecordResponse findLatest(UUID cowId) {
        getCow(cowId);
        return healthRecordRepository.findTopByCowIdAndDeletedFalseOrderByRecordDateDesc(cowId)
                .map(mapper::toHealthRecordResponse)
                .orElseThrow(() -> new ResourceNotFoundException("No health records found for cow: " + cowId));
    }

    @Transactional
    public void delete(UUID cowId, UUID recordId) {
        HealthRecord record = healthRecordRepository.findByIdAndCowIdAndDeletedFalse(recordId, cowId)
                .orElseThrow(() -> new ResourceNotFoundException("Health record not found: " + recordId));
        record.setDeleted(true);
        healthRecordRepository.save(record);
    }

    private Cow getCow(UUID cowId) {
        return cowRepository.findByIdAndDeletedFalse(cowId)
                .orElseThrow(() -> new ResourceNotFoundException("Cow not found: " + cowId));
    }
}
