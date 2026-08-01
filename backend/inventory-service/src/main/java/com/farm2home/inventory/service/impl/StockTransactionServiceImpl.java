package com.farm2home.inventory.service.impl;

import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.InventoryConsumptionPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.reports.InventoryReportRow;
import com.farm2home.common.core.reports.InventoryReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.inventory.domain.entity.InventoryItem;
import com.farm2home.inventory.domain.entity.StockTransaction;
import com.farm2home.inventory.domain.enums.TxnType;
import com.farm2home.inventory.domain.repository.InventoryItemRepository;
import com.farm2home.inventory.domain.repository.StockTransactionRepository;
import com.farm2home.inventory.domain.repository.StockTransactionSpecifications;
import com.farm2home.inventory.dto.request.StockTransactionRequest;
import com.farm2home.inventory.dto.response.StockTransactionResponse;
import com.farm2home.inventory.exception.InventoryException;
import com.farm2home.inventory.kafka.InventoryEventProducer;
import com.farm2home.inventory.mapper.InventoryMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockTransactionServiceImpl {

    private final StockTransactionRepository txnRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryItemServiceImpl itemService;
    private final InventoryMapper mapper;
    private final InventoryEventProducer eventProducer;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;

    @Transactional
    public StockTransactionResponse transact(UUID itemId, StockTransactionRequest request) {
        InventoryItem item = itemService.getItem(itemId);
        BigDecimal previousQuantity = item.getQuantity();
        boolean isReduction = request.getTxnType() == TxnType.OUT;

        if (isReduction) {
            if (item.getQuantity().compareTo(request.getQuantity()) < 0) {
                throw new InventoryException("Insufficient stock for item " + itemId
                        + ". Available: " + item.getQuantity() + ", requested: " + request.getQuantity());
            }
            item.setQuantity(item.getQuantity().subtract(request.getQuantity()));
        } else {
            item.setQuantity(item.getQuantity().add(request.getQuantity()));
        }
        itemRepository.save(item);
        eventProducer.publishInventoryUpdated(item, previousQuantity);

        StockTransaction txn = mapper.toEntity(request);
        txn.setItem(item);
        txn.setCreatedBy(currentUser());

        StockTransactionResponse response = mapper.toTxnResponse(txnRepository.save(txn));
        auditLogService.record(AuditEntry.builder()
                .action(AuditAction.UPDATE)
                .entityType("InventoryItem")
                .entityId(item.getId().toString())
                .oldValue(previousQuantity.toString())
                .newValue(item.getQuantity().toString())
                .details((isReduction ? "Stock reduced" : "Stock added") + " for item " + itemId)
                .build());

        return response;
    }

    @Transactional(readOnly = true)
    public Page<StockTransactionResponse> findByItem(UUID itemId, Pageable pageable) {
        itemService.getItem(itemId); // verify exists
        return txnRepository.findAllByItemIdOrderByTransactedAtDesc(itemId, pageable)
                .map(mapper::toTxnResponse);
    }

    @Transactional(readOnly = true)
    public ReportPage<InventoryReportRow, InventoryReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            TxnType txnType, UUID itemId, Pageable pageable) {
        Specification<StockTransaction> dateSpec = Specification.where(null);
        if (dateFrom != null || dateTo != null) {
            dateSpec = dateSpec.and(StockTransactionSpecifications.transactedBetween(dateFrom, dateTo));
        }
        if (itemId != null) {
            dateSpec = dateSpec.and(StockTransactionSpecifications.hasItem(itemId));
        }
        Specification<StockTransaction> spec = txnType != null
                ? dateSpec.and(StockTransactionSpecifications.hasTxnType(txnType)) : dateSpec;

        Page<StockTransaction> page = txnRepository.findAll(spec, pageable);
        List<InventoryReportRow> rows = page.getContent().stream()
                .map(t -> InventoryReportRow.builder()
                        .transactionId(t.getId())
                        .itemId(t.getItem().getId())
                        .itemName(t.getItem().getItemName())
                        .txnType(t.getTxnType().name())
                        .quantity(t.getQuantity())
                        .transactedAt(t.getTransactedAt())
                        .build())
                .toList();

        InventoryReportSummary summary = InventoryReportSummary.builder()
                .totalTransactions(page.getTotalElements())
                .totalInQuantity(sumQuantity(dateSpec.and(StockTransactionSpecifications.hasTxnType(TxnType.IN))))
                .totalOutQuantity(sumQuantity(dateSpec.and(StockTransactionSpecifications.hasTxnType(TxnType.OUT))))
                .build();

        return ReportPage.<InventoryReportRow, InventoryReportSummary>builder()
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
    private BigDecimal sumQuantity(Specification<StockTransaction> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> cq = cb.createQuery(BigDecimal.class);
        Root<StockTransaction> root = cq.from(StockTransaction.class);
        cq.select(cb.coalesce(cb.sum(root.get("quantity")), BigDecimal.ZERO));
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        return entityManager.createQuery(cq).getSingleResult();
    }

    @Transactional(readOnly = true)
    public TrendSeries<InventoryConsumptionPoint> getConsumptionTrend(Granularity granularity, LocalDate dateFrom,
            LocalDate dateTo) {
        LocalDateTime start = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime endExclusive = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;

        List<InventoryConsumptionPoint> points = txnRepository.findConsumptionTrend(
                        granularity.getSqlUnit(), start, endExclusive)
                .stream()
                .map(row -> InventoryConsumptionPoint.builder()
                        .period(row.getPeriod())
                        .consumedQuantity(row.getConsumedQuantity())
                        .build())
                .toList();

        return TrendSeries.<InventoryConsumptionPoint>builder()
                .granularity(granularity).from(dateFrom).to(dateTo).points(points).build();
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "system";
        Object principal = auth.getPrincipal();
        if (principal instanceof com.farm2home.inventory.config.UserPrincipal up) return up.mobile();
        return auth.getName();
    }
}
