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
}
