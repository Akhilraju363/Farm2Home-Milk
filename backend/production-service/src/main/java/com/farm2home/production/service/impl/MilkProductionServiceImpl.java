package com.farm2home.production.service.impl;

import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.ProductionTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.Audited;
import com.farm2home.common.core.dashboard.ProductionSummaryResponse;
import com.farm2home.common.core.reports.ProductionReportRow;
import com.farm2home.common.core.reports.ProductionReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.production.domain.entity.MilkProduction;
import com.farm2home.production.domain.enums.QualityGrade;
import com.farm2home.production.domain.repository.MilkProductionRepository;
import com.farm2home.production.domain.repository.MilkProductionSpecifications;
import com.farm2home.production.dto.request.CreateMilkProductionRequest;
import com.farm2home.production.dto.request.UpdateMilkProductionRequest;
import com.farm2home.production.dto.response.DailySummaryResponse;
import com.farm2home.production.dto.response.MilkProductionResponse;
import com.farm2home.production.exception.ProductionException;
import com.farm2home.production.exception.ResourceNotFoundException;
import com.farm2home.production.mapper.ProductionMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MilkProductionServiceImpl {

    private final MilkProductionRepository repository;
    private final ProductionMapper mapper;
    private final EntityManager entityManager;

    @Transactional
    @Audited(action = AuditAction.CREATE, entityType = "MilkProduction")
    public MilkProductionResponse create(CreateMilkProductionRequest request) {
        if (repository.existsByCowIdAndCollectionDateAndSessionAndDeletedFalse(
                request.getCowId(), request.getCollectionDate(), request.getSession())) {
            throw new ProductionException(
                    "Production record already exists for cow " + request.getCowId()
                    + " on " + request.getCollectionDate() + " (" + request.getSession() + ")");
        }
        MilkProduction entity = mapper.toEntity(request);
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

    @Transactional(readOnly = true)
    public ProductionSummaryResponse getSummary() {
        BigDecimal total = repository.sumQuantityByCollectionDate(LocalDate.now());
        return ProductionSummaryResponse.builder()
                .totalLitersToday(total != null ? total : BigDecimal.ZERO)
                .build();
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "MilkProduction")
    public MilkProductionResponse update(UUID id, UpdateMilkProductionRequest request) {
        MilkProduction record = getRecord(id);
        mapper.updateEntityFromRequest(request, record);
        return mapper.toResponse(repository.save(record));
    }

    @Transactional
    @Audited(action = AuditAction.DELETE, entityType = "MilkProduction")
    public void delete(UUID id) {
        MilkProduction record = getRecord(id);
        record.setDeleted(true);
        repository.save(record);
    }

    @Transactional(readOnly = true)
    public ReportPage<ProductionReportRow, ProductionReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            QualityGrade qualityGrade, List<UUID> cowIds, Pageable pageable) {
        Specification<MilkProduction> spec = Specification.where(MilkProductionSpecifications.notDeleted());
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(MilkProductionSpecifications.collectionDateBetween(dateFrom, dateTo));
        }
        if (qualityGrade != null) {
            spec = spec.and(MilkProductionSpecifications.hasQualityGrade(qualityGrade));
        }
        if (cowIds != null && !cowIds.isEmpty()) {
            spec = spec.and(MilkProductionSpecifications.hasCowIdIn(cowIds));
        }

        Page<MilkProduction> page = repository.findAll(spec, pageable);
        List<ProductionReportRow> rows = page.getContent().stream()
                .map(m -> ProductionReportRow.builder()
                        .productionId(m.getId())
                        .cowId(m.getCowId())
                        .collectionDate(m.getCollectionDate())
                        .session(m.getSession().name())
                        .quantityLiters(m.getQuantityLiters())
                        .qualityGrade(m.getQualityGrade() != null ? m.getQualityGrade().name() : null)
                        .build())
                .toList();

        ProductionReportSummary summary = ProductionReportSummary.builder()
                .totalRecords(page.getTotalElements())
                .totalLiters(sumLiters(spec))
                .build();

        return ReportPage.<ProductionReportRow, ProductionReportSummary>builder()
                .content(rows)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .summary(summary)
                .build();
    }

    /** Reuses the same Specification that builds the page's WHERE clause, so the aggregate
     *  total is always computed over exactly the same filtered set - just a different SELECT. */
    private BigDecimal sumLiters(Specification<MilkProduction> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> cq = cb.createQuery(BigDecimal.class);
        Root<MilkProduction> root = cq.from(MilkProduction.class);
        cq.select(cb.coalesce(cb.sum(root.get("quantityLiters")), BigDecimal.ZERO));
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        return entityManager.createQuery(cq).getSingleResult();
    }

    @Transactional(readOnly = true)
    public TrendSeries<ProductionTrendPoint> getProductionTrend(Granularity granularity, LocalDate dateFrom, LocalDate dateTo) {
        List<ProductionTrendPoint> points = repository.findProductionTrend(granularity.getSqlUnit(), dateFrom, dateTo)
                .stream()
                .map(row -> ProductionTrendPoint.builder().period(row.getPeriod()).totalLiters(row.getTotalLiters()).build())
                .toList();

        return TrendSeries.<ProductionTrendPoint>builder()
                .granularity(granularity).from(dateFrom).to(dateTo).points(points).build();
    }

    private MilkProduction getRecord(UUID id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Production record not found: " + id));
    }
}
