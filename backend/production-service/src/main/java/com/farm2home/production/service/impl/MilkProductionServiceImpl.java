package com.farm2home.production.service.impl;

import com.farm2home.production.domain.entity.MilkProduction;
import com.farm2home.production.domain.repository.MilkProductionRepository;
import com.farm2home.production.dto.request.CreateMilkProductionRequest;
import com.farm2home.production.dto.request.UpdateMilkProductionRequest;
import com.farm2home.production.dto.response.DailySummaryResponse;
import com.farm2home.production.dto.response.MilkProductionResponse;
import com.farm2home.production.exception.ProductionException;
import com.farm2home.production.exception.ResourceNotFoundException;
import com.farm2home.production.mapper.ProductionMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilkProductionServiceImpl {

    private final MilkProductionRepository repository;
    private final ProductionMapper mapper;

    @Transactional
    public MilkProductionResponse create(CreateMilkProductionRequest request) {
        if (repository.existsByCowIdAndCollectionDateAndSessionAndDeletedFalse(
                request.getCowId(), request.getCollectionDate(), request.getSession())) {
            throw new ProductionException(
                    "Production record already exists for cow " + request.getCowId()
                    + " on " + request.getCollectionDate() + " (" + request.getSession() + ")");
        }
        MilkProduction entity = MilkProduction.builder()
                .cowId(request.getCowId())
                .collectionDate(request.getCollectionDate())
                .session(request.getSession())
                .quantityLiters(request.getQuantityLiters())
                .fatPercentage(request.getFatPercentage())
                .snfPercentage(request.getSnfPercentage())
                .qualityGrade(request.getQualityGrade())
                .collectedBy(request.getCollectedBy())
                .notes(request.getNotes())
                .build();
        return mapper.toResponse(repository.save(entity));
    }

    @Transactional(readOnly = true)
    public Page<MilkProductionResponse> findAll(Pageable pageable) {
        return repository.findAllByDeletedFalse(pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public MilkProductionResponse findById(UUID id) {
        return mapper.toResponse(getRecord(id));
    }

    @Transactional(readOnly = true)
    public Page<MilkProductionResponse> findByCow(UUID cowId, Pageable pageable) {
        return repository.findAllByCowIdAndDeletedFalse(cowId, pageable).map(mapper::toResponse);
    }

    @Transactional(readOnly = true)
    public List<DailySummaryResponse> getDailySummaryByCow(UUID cowId, LocalDate from, LocalDate to) {
        return repository.findDailySummaryByCow(cowId, from, to);
    }

    @Transactional(readOnly = true)
    public List<DailySummaryResponse> getDailySummary(LocalDate from, LocalDate to) {
        return repository.findDailySummary(from, to);
    }

    @Transactional
    public MilkProductionResponse update(UUID id, UpdateMilkProductionRequest request) {
        MilkProduction record = getRecord(id);
        if (request.getQuantityLiters() != null) record.setQuantityLiters(request.getQuantityLiters());
        if (request.getFatPercentage()  != null) record.setFatPercentage(request.getFatPercentage());
        if (request.getSnfPercentage()  != null) record.setSnfPercentage(request.getSnfPercentage());
        if (request.getQualityGrade()   != null) record.setQualityGrade(request.getQualityGrade());
        if (request.getCollectedBy()    != null) record.setCollectedBy(request.getCollectedBy());
        if (request.getNotes()          != null) record.setNotes(request.getNotes());
        return mapper.toResponse(repository.save(record));
    }

    @Transactional
    public void delete(UUID id) {
        MilkProduction record = getRecord(id);
        record.setDeleted(true);
        repository.save(record);
    }

    private MilkProduction getRecord(UUID id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Production record not found: " + id));
    }
}
