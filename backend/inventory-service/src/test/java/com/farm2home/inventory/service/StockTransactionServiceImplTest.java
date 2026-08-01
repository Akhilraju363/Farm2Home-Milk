package com.farm2home.inventory.service;

import com.farm2home.inventory.domain.entity.InventoryItem;
import com.farm2home.inventory.domain.entity.StockTransaction;
import com.farm2home.inventory.domain.enums.ItemType;
import com.farm2home.inventory.domain.enums.TxnType;
import com.farm2home.inventory.domain.enums.UnitType;
import com.farm2home.inventory.domain.repository.InventoryItemRepository;
import com.farm2home.inventory.domain.repository.StockTransactionRepository;
import com.farm2home.inventory.dto.request.StockTransactionRequest;
import com.farm2home.inventory.dto.response.StockTransactionResponse;
import com.farm2home.inventory.exception.InventoryException;
import com.farm2home.common.core.audit.AuditEntry;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.inventory.kafka.InventoryEventProducer;
import com.farm2home.inventory.mapper.InventoryMapper;
import com.farm2home.inventory.service.impl.InventoryItemServiceImpl;
import com.farm2home.inventory.service.impl.StockTransactionServiceImpl;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.InventoryConsumptionPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.reports.InventoryReportRow;
import com.farm2home.common.core.reports.InventoryReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaQuery;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StockTransactionServiceImplTest {

    @Mock private StockTransactionRepository txnRepository;
    @Mock private InventoryItemRepository itemRepository;
    @Mock private InventoryItemServiceImpl itemService;
    @Mock private InventoryMapper mapper;
    @Mock private InventoryEventProducer eventProducer;
    @Mock private AuditLogService auditLogService;
    @Mock(answer = Answers.RETURNS_DEEP_STUBS) private EntityManager entityManager;

    @InjectMocks private StockTransactionServiceImpl service;

    private final UUID itemId = UUID.randomUUID();

    private InventoryItem buildItem(BigDecimal quantity) {
        return InventoryItem.builder()
                .id(itemId).itemName("Rice Straw")
                .itemType(ItemType.FEED).unit(UnitType.KG)
                .quantity(quantity).reorderLevel(new BigDecimal("50.00"))
                .deleted(false).build();
    }

    @Nested @DisplayName("transact()")
    class Transact {

        @Test
        @DisplayName("stock IN → increases item quantity")
        void stockIn() {
            InventoryItem item = buildItem(new BigDecimal("100.00"));
            when(itemService.getItem(itemId)).thenReturn(item);
            when(mapper.toEntity(any(StockTransactionRequest.class))).thenReturn(new StockTransaction());
            StockTransaction txn = StockTransaction.builder().id(UUID.randomUUID()).item(item)
                    .txnType(TxnType.IN).quantity(new BigDecimal("25.00")).build();
            when(txnRepository.save(any())).thenReturn(txn);
            when(mapper.toTxnResponse(txn)).thenReturn(
                    StockTransactionResponse.builder().txnType("IN").quantity(new BigDecimal("25.00")).build());

            StockTransactionRequest req = new StockTransactionRequest();
            req.setTxnType(TxnType.IN);
            req.setQuantity(new BigDecimal("25.00"));

            service.transact(itemId, req);

            assertThat(item.getQuantity()).isEqualByComparingTo("125.00");
            verify(itemRepository).save(item);
            verify(auditLogService).record(argThat(entry ->
                    "100.00".equals(entry.getOldValue()) && "125.00".equals(entry.getNewValue())));
        }

        @Test
        @DisplayName("stock OUT with sufficient quantity → decreases item quantity")
        void stockOut_sufficient() {
            InventoryItem item = buildItem(new BigDecimal("100.00"));
            when(itemService.getItem(itemId)).thenReturn(item);
            when(mapper.toEntity(any(StockTransactionRequest.class))).thenReturn(new StockTransaction());
            StockTransaction txn = StockTransaction.builder().id(UUID.randomUUID()).item(item)
                    .txnType(TxnType.OUT).quantity(new BigDecimal("30.00")).build();
            when(txnRepository.save(any())).thenReturn(txn);
            when(mapper.toTxnResponse(txn)).thenReturn(
                    StockTransactionResponse.builder().txnType("OUT").quantity(new BigDecimal("30.00")).build());

            StockTransactionRequest req = new StockTransactionRequest();
            req.setTxnType(TxnType.OUT);
            req.setQuantity(new BigDecimal("30.00"));

            service.transact(itemId, req);

            assertThat(item.getQuantity()).isEqualByComparingTo("70.00");
            verify(auditLogService).record(argThat(entry ->
                    "100.00".equals(entry.getOldValue()) && "70.00".equals(entry.getNewValue())));
        }

        @Test
        @DisplayName("stock OUT exceeding available quantity → throws InventoryException")
        void stockOut_insufficient() {
            InventoryItem item = buildItem(new BigDecimal("20.00"));
            when(itemService.getItem(itemId)).thenReturn(item);

            StockTransactionRequest req = new StockTransactionRequest();
            req.setTxnType(TxnType.OUT);
            req.setQuantity(new BigDecimal("50.00"));

            assertThatThrownBy(() -> service.transact(itemId, req))
                    .isInstanceOf(InventoryException.class)
                    .hasMessageContaining("Insufficient stock");
            verify(txnRepository, never()).save(any());
            verify(auditLogService, never()).record(any(AuditEntry.class));
        }
    }

    @Nested @DisplayName("findByItem()")
    class FindByItem {

        @Test
        @DisplayName("verifies item exists and returns mapped page")
        void returnsMappedPage() {
            InventoryItem item = buildItem(new BigDecimal("100.00"));
            when(itemService.getItem(itemId)).thenReturn(item);
            StockTransaction txn = StockTransaction.builder().id(UUID.randomUUID()).build();
            when(txnRepository.findAllByItemIdOrderByTransactedAtDesc(eq(itemId), any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(txn)));
            when(mapper.toTxnResponse(txn)).thenReturn(StockTransactionResponse.builder().build());

            var result = service.findByItem(itemId, org.springframework.data.domain.Pageable.unpaged());

            assertThat(result.getTotalElements()).isEqualTo(1);
            verify(itemService).getItem(itemId);
        }
    }

    @Nested @DisplayName("currentUser() (exercised via transact())")
    class CurrentUser {

        @org.junit.jupiter.api.AfterEach
        void clearContext() {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
        }

        private void stubTransactHappyPath(StockTransaction txnToReturn) {
            InventoryItem item = buildItem(new BigDecimal("100.00"));
            when(itemService.getItem(itemId)).thenReturn(item);
            when(mapper.toEntity(any(StockTransactionRequest.class))).thenReturn(new StockTransaction());
            when(txnRepository.save(any())).thenReturn(txnToReturn);
            when(mapper.toTxnResponse(txnToReturn)).thenReturn(StockTransactionResponse.builder().build());
        }

        @Test
        @DisplayName("no authentication in context → createdBy is \"system\"")
        void noAuth_system() {
            org.springframework.security.core.context.SecurityContextHolder.clearContext();
            org.mockito.ArgumentCaptor<StockTransaction> captor =
                    org.mockito.ArgumentCaptor.forClass(StockTransaction.class);
            stubTransactHappyPath(StockTransaction.builder().build());

            StockTransactionRequest req = new StockTransactionRequest();
            req.setTxnType(TxnType.IN);
            req.setQuantity(new BigDecimal("10.00"));
            service.transact(itemId, req);

            verify(txnRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo("system");
        }

        @Test
        @DisplayName("authenticated with UserPrincipal → createdBy is the principal's mobile")
        void userPrincipal_usesMobile() {
            var principal = new com.farm2home.inventory.config.UserPrincipal(
                    UUID.randomUUID(), "9876543210", java.util.Set.of("CUSTOMER"));
            var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    principal, null, java.util.List.of());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

            org.mockito.ArgumentCaptor<StockTransaction> captor =
                    org.mockito.ArgumentCaptor.forClass(StockTransaction.class);
            stubTransactHappyPath(StockTransaction.builder().build());

            StockTransactionRequest req = new StockTransactionRequest();
            req.setTxnType(TxnType.IN);
            req.setQuantity(new BigDecimal("10.00"));
            service.transact(itemId, req);

            verify(txnRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo("9876543210");
        }

        @Test
        @DisplayName("authenticated with a non-UserPrincipal principal → createdBy falls back to Authentication#getName()")
        void otherPrincipal_usesAuthName() {
            var auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                    "some-other-name", null, java.util.List.of());
            org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(auth);

            org.mockito.ArgumentCaptor<StockTransaction> captor =
                    org.mockito.ArgumentCaptor.forClass(StockTransaction.class);
            stubTransactHappyPath(StockTransaction.builder().build());

            StockTransactionRequest req = new StockTransactionRequest();
            req.setTxnType(TxnType.IN);
            req.setQuantity(new BigDecimal("10.00"));
            service.transact(itemId, req);

            verify(txnRepository).save(captor.capture());
            assertThat(captor.getValue().getCreatedBy()).isEqualTo("some-other-name");
        }
    }

    // ── GetReport (Inventory Report) ────────────────────────────────────────────

    @Nested
    @DisplayName("getReport()")
    class GetReport {

        @Test
        @DisplayName("maps the repository page into report rows and totals")
        void happyPath() {
            InventoryItem item = buildItem(new BigDecimal("100.00"));
            StockTransaction txn = StockTransaction.builder()
                    .id(UUID.randomUUID()).item(item).txnType(TxnType.IN)
                    .quantity(new BigDecimal("25.00")).build();
            var page = new PageImpl<>(List.of(txn), PageRequest.of(0, 20), 1);
            when(txnRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(entityManager.createQuery(any(CriteriaQuery.class)).getSingleResult())
                    .thenReturn(new BigDecimal("25.00"));

            ReportPage<InventoryReportRow, InventoryReportSummary> result = service.getReport(
                    null, null, TxnType.IN, itemId, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
            assertThat(result.getContent().get(0).getItemName()).isEqualTo("Rice Straw");
            assertThat(result.getContent().get(0).getTxnType()).isEqualTo("IN");
            assertThat(result.getTotalElements()).isEqualTo(1);
            assertThat(result.getSummary().getTotalTransactions()).isEqualTo(1);
            assertThat(result.getSummary().getTotalInQuantity()).isEqualByComparingTo("25.00");
            assertThat(result.getSummary().getTotalOutQuantity()).isEqualByComparingTo("25.00");
        }

        @Test
        @DisplayName("no matching transactions → empty content with zeroed summary")
        void noResults() {
            var page = new PageImpl<StockTransaction>(List.of(), PageRequest.of(0, 20), 0);
            when(txnRepository.findAll(any(Specification.class), any(PageRequest.class))).thenReturn(page);
            when(entityManager.createQuery(any(CriteriaQuery.class)).getSingleResult())
                    .thenReturn(BigDecimal.ZERO);

            ReportPage<InventoryReportRow, InventoryReportSummary> result = service.getReport(
                    LocalDate.now().minusDays(7), LocalDate.now(), null, null, PageRequest.of(0, 20));

            assertThat(result.getContent()).isEmpty();
            assertThat(result.getSummary().getTotalTransactions()).isZero();
            assertThat(result.getSummary().getTotalInQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
            assertThat(result.getSummary().getTotalOutQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
        }
    }

    @Nested
    @DisplayName("getConsumptionTrend()")
    class GetConsumptionTrend {

        @Test
        @DisplayName("maps already-aggregated repository rows into trend points")
        void mapsRows() {
            StockTransactionRepository.InventoryConsumptionRow row =
                    mock(StockTransactionRepository.InventoryConsumptionRow.class);
            when(row.getPeriod()).thenReturn(LocalDate.of(2026, 1, 1));
            when(row.getConsumedQuantity()).thenReturn(new BigDecimal("40.00"));
            when(txnRepository.findConsumptionTrend(eq("day"), any(), any())).thenReturn(List.of(row));

            TrendSeries<InventoryConsumptionPoint> result = service.getConsumptionTrend(
                    Granularity.DAILY, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31));

            assertThat(result.getGranularity()).isEqualTo(Granularity.DAILY);
            assertThat(result.getPoints()).hasSize(1);
            assertThat(result.getPoints().get(0).getPeriod()).isEqualTo(LocalDate.of(2026, 1, 1));
            assertThat(result.getPoints().get(0).getConsumedQuantity()).isEqualByComparingTo("40.00");
        }

        @Test
        @DisplayName("no rows → empty points list")
        void noRows_emptyPoints() {
            when(txnRepository.findConsumptionTrend(eq("month"), any(), any())).thenReturn(List.of());

            TrendSeries<InventoryConsumptionPoint> result = service.getConsumptionTrend(Granularity.MONTHLY, null, null);

            assertThat(result.getPoints()).isEmpty();
        }
    }
}
