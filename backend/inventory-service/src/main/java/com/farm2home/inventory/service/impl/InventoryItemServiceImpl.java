package com.farm2home.inventory.service.impl;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.audit.Audited;
import com.farm2home.common.core.dashboard.InventorySummaryResponse;
import com.farm2home.common.core.dashboard.LowStockItemSummary;
import com.farm2home.common.export.BatchSupplier;
import com.farm2home.common.export.ExportColumn;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.export.TabularExporterFactory;
import com.farm2home.inventory.domain.entity.InventoryItem;
import com.farm2home.inventory.domain.enums.ItemType;
import com.farm2home.inventory.domain.repository.InventoryItemRepository;
import com.farm2home.inventory.domain.repository.InventoryItemSpecifications;
import com.farm2home.inventory.dto.request.CreateInventoryItemRequest;
import com.farm2home.inventory.dto.request.UpdateInventoryItemRequest;
import com.farm2home.inventory.dto.response.InventoryItemResponse;
import com.farm2home.inventory.exception.ResourceNotFoundException;
import com.farm2home.inventory.kafka.InventoryEventProducer;
import com.farm2home.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryItemServiceImpl {

    private final InventoryItemRepository repository;
    private final InventoryMapper mapper;
    private final InventoryEventProducer eventProducer;
    private final AuditLogService auditLogService;

    @Transactional
    @Audited(action = AuditAction.CREATE, entityType = "InventoryItem")
    public InventoryItemResponse create(CreateInventoryItemRequest request) {
        InventoryItem item = mapper.toEntity(request);
        return mapper.toItemResponse(repository.save(item));
    }

    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> findAll(com.farm2home.inventory.domain.enums.ItemType typeFilter, Pageable pageable) {
        if (typeFilter != null) {
            return repository.findAllByItemTypeAndDeletedFalse(typeFilter, pageable).map(mapper::toItemResponse);
        }
        return repository.findAllByDeletedFalse(pageable).map(mapper::toItemResponse);
    }

    @Transactional(readOnly = true)
    public InventoryItemResponse findById(UUID id) {
        return mapper.toItemResponse(getItem(id));
    }

    @Transactional(readOnly = true)
    public List<InventoryItemResponse> findLowStock() {
        return repository.findItemsBelowReorderLevel().stream().map(mapper::toItemResponse).toList();
    }

    @Transactional(readOnly = true)
    public InventorySummaryResponse getSummary(int limit) {
        long count = repository.countItemsBelowReorderLevel();
        List<LowStockItemSummary> topItems = repository.findItemsBelowReorderLevel().stream()
                .limit(limit)
                .map(item -> LowStockItemSummary.builder()
                        .id(item.getId())
                        .name(item.getItemName())
                        .quantity(item.getQuantity())
                        .reorderLevel(item.getReorderLevel())
                        .build())
                .toList();
        return InventorySummaryResponse.builder().lowStockCount(count).topItems(topItems).build();
    }

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "InventoryItem")
    public InventoryItemResponse update(UUID id, UpdateInventoryItemRequest request) {
        InventoryItem item = getItem(id);
        mapper.updateItemFromRequest(request, item);
        return mapper.toItemResponse(repository.save(item));
    }

    @Transactional
    @Audited(action = AuditAction.DELETE, entityType = "InventoryItem")
    public void delete(UUID id) {
        InventoryItem item = getItem(id);
        item.setDeleted(true);
        repository.save(item);
    }

    public InventoryItem getItem(UUID id) {
        return repository.findByIdAndDeletedFalse(id)
                .orElseThrow(() -> new ResourceNotFoundException("Inventory item not found: " + id));
    }

    @Transactional(readOnly = true)
    public Page<InventoryItemResponse> search(String keyword, LocalDate dateFrom, LocalDate dateTo,
            ItemType itemType, Pageable pageable) {
        Specification<InventoryItem> spec = buildSearchSpecification(keyword, dateFrom, dateTo, itemType);
        return repository.findAll(spec, pageable).map(mapper::toItemResponse);
    }

    /** Shared by both {@link #search} and {@link #export} so the two always see the exact same
     *  filtered result set. */
    private Specification<InventoryItem> buildSearchSpecification(String keyword, LocalDate dateFrom, LocalDate dateTo,
            ItemType itemType) {
        Specification<InventoryItem> spec = Specification.where(InventoryItemSpecifications.notDeleted());
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(InventoryItemSpecifications.hasKeyword(keyword));
        }
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(InventoryItemSpecifications.createdBetween(dateFrom, dateTo));
        }
        if (itemType != null) {
            spec = spec.and(InventoryItemSpecifications.hasItemType(itemType));
        }
        return spec;
    }

    /** Streams matching inventory items straight to {@code out} in the requested file format, one
     *  bounded page at a time, so exporting a very large inventory never requires holding the full
     *  result set in memory. Each batch fetch runs in its own short-lived Spring Data transaction
     *  (this method is deliberately NOT wrapped in a single @Transactional so a slow export doesn't
     *  pin one DB connection for its entire duration). Runs on the async StreamingResponseBody
     *  dispatch thread, not the original request thread. */
    public void export(ExportFormat format, OutputStream out, String keyword, LocalDate dateFrom, LocalDate dateTo,
            ItemType itemType, String sortBy, boolean ascending) throws IOException {
        Specification<InventoryItem> spec = buildSearchSpecification(keyword, dateFrom, dateTo, itemType);
        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        List<ExportColumn<InventoryItem>> columns = List.of(
                new ExportColumn<>("Item Name", InventoryItem::getItemName),
                new ExportColumn<>("Item Type", i -> i.getItemType().name()),
                new ExportColumn<>("Quantity", i -> i.getQuantity().toString()),
                new ExportColumn<>("Unit", i -> i.getUnit().name()),
                new ExportColumn<>("Reorder Level", i -> i.getReorderLevel().toString()),
                new ExportColumn<>("Unit Price", i -> i.getUnitPrice() == null ? "" : i.getUnitPrice().toString()),
                new ExportColumn<>("Supplier", i -> i.getSupplier() == null ? "" : i.getSupplier()),
                new ExportColumn<>("Created At", i -> i.getCreatedAt() == null ? "" : i.getCreatedAt().toString()));

        BatchSupplier<InventoryItem> supplier = (page, size) ->
                repository.findAll(spec, PageRequest.of(page, size, sort)).getContent();

        TabularExporterFactory.<InventoryItem>forFormat(format)
                .write(out, columns.stream().map(ExportColumn::header).toList(), columns, supplier);
    }

    // ── Scheduled jobs ──────────────────────────────────────────────────────────

    /** Daily at 08:00 - sweep all items and re-alert on anything still at/below reorder
     *  level, independent of whether a stock transaction happened (a slow-moving low-stock
     *  item that nobody restocks would otherwise never trigger a fresh alert). */
    @Scheduled(cron = "0 0 8 * * *")
    @Transactional(readOnly = true)
    public void alertLowStockItems() {
        List<InventoryItem> lowStockItems = repository.findItemsBelowReorderLevel();
        if (lowStockItems.isEmpty()) {
            return;
        }
        log.info("Low stock sweep: {} item(s) at or below reorder level.", lowStockItems.size());
        lowStockItems.forEach(item -> {
            eventProducer.publishLowStockAlert(item);
            auditLogService.record(AuditEntry.builder()
                    .action(AuditAction.UPDATE)
                    .entityType("InventoryItem")
                    .entityId(item.getId().toString())
                    .details("Low stock alert: quantity " + item.getQuantity()
                            + " at or below reorder level " + item.getReorderLevel())
                    .build());
        });
    }
}
