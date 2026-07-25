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
import com.farm2home.inventory.mapper.InventoryMapper;
import com.farm2home.inventory.service.impl.InventoryItemServiceImpl;
import com.farm2home.inventory.service.impl.StockTransactionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
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
}
