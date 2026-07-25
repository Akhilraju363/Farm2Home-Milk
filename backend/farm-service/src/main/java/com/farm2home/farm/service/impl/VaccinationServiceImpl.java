package com.farm2home.farm.service.impl;

import com.farm2home.farm.domain.entity.Cow;
import com.farm2home.farm.domain.entity.Vaccination;
import com.farm2home.farm.domain.repository.CowRepository;
import com.farm2home.farm.domain.repository.VaccinationRepository;
import com.farm2home.farm.dto.request.CreateVaccinationRequest;
import com.farm2home.farm.dto.response.VaccinationResponse;
import com.farm2home.farm.exception.ResourceNotFoundException;
import com.farm2home.farm.mapper.FarmMapper;
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
public class VaccinationServiceImpl {

    private final VaccinationRepository vaccinationRepository;
    private final CowRepository cowRepository;
    private final FarmMapper mapper;

    @Transactional
    public VaccinationResponse create(UUID cowId, CreateVaccinationRequest request) {
        Cow cow = getCow(cowId);
        Vaccination vaccination = mapper.toEntity(request);
        vaccination.setCow(cow);
        return mapper.toVaccinationResponse(vaccinationRepository.save(vaccination));
    }

    @Transactional(readOnly = true)
    public Page<VaccinationResponse> findByCow(UUID cowId, Pageable pageable) {
        getCow(cowId); // verify cow exists
        return vaccinationRepository.findAllByCowIdAndDeletedFalseOrderByAdministeredAtDesc(cowId, pageable)
                .map(mapper::toVaccinationResponse);
    }

    @Transactional(readOnly = true)
    public List<VaccinationResponse> findUpcoming(int daysAhead) {
        LocalDate from = LocalDate.now();
        LocalDate to   = from.plusDays(daysAhead);
        return vaccinationRepository.findUpcomingVaccinations(from, to).stream()
                .map(mapper::toVaccinationResponse).toList();
    }

    @Transactional
    public void delete(UUID cowId, UUID vaccinationId) {
        Vaccination v = vaccinationRepository.findByIdAndCowIdAndDeletedFalse(vaccinationId, cowId)
                .orElseThrow(() -> new ResourceNotFoundException("Vaccination not found: " + vaccinationId));
        v.setDeleted(true);
        vaccinationRepository.save(v);
    }

    private Cow getCow(UUID cowId) {
        return cowRepository.findByIdAndDeletedFalse(cowId)
                .orElseThrow(() -> new ResourceNotFoundException("Cow not found: " + cowId));
    }
}
