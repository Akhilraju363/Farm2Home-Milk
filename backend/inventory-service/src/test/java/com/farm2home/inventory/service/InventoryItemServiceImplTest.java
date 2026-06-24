package com.farm2home.inventory.service;

import com.farm2home.inventory.domain.entity.InventoryItem;
import com.farm2home.inventory.domain.enums.ItemType;
import com.farm2home.inventory.domain.enums.TxnType;
import com.farm2home.inventory.domain.enums.UnitType;
import com.farm2home.inventory.domain.repository.InventoryItemRepository;
import com.farm2home.inventory.domain.repository.StockTransactionRepository;
import com.farm2home.inventory.dto.request.CreateInventoryItemRequest;
import com.farm2home.inventory.dto.request.StockTransactionRequest;
import com.farm2home.inventory.dto.request.UpdateInventoryItemRequest;
import com.farm2home.inventory.dto.response.InventoryItemResponse;
import com.farm2home.inventory.exception.InventoryException;
import com.farm2home.inventory.exception.ResourceNotFoundException;
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
class InventoryItemServiceImplTest {

    @Mock private InventoryItemRepository repository;
    @Mock private InventoryMapper mapper;

    @InjectMocks private InventoryItemServiceImpl service;

    private final UUID itemId = UUID.randomUUID();

    private InventoryItem buildItem(BigDecimal quantity) {
        return InventoryItem.builder()
                .id(itemId)
                .itemName("Rice Straw")
                .itemType(ItemType.FEED)
                .unit(UnitType.KG)
                .quantity(quantity)
                .reorderLevel(new BigDecimal("50.00"))
                .deleted(false)
                .build();
    }

    private InventoryItemResponse buildResponse(BigDecimal quantity) {
        return InventoryItemResponse.builder()
                .id(itemId).itemName("Rice Straw")
                .itemType("FEED").unit("KG")
                .quantity(quantity)
                .reorderLevel(new BigDecimal("50.00"))
                .build();
    }

    // ── Create ───────────────────────────────────────────────────────────────────

    @Nested @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("valid request → saves item with zero quantity")
        void happyPath() {
            InventoryItem saved = buildItem(BigDecimal.ZERO);
            when(repository.save(any())).thenReturn(saved);
            when(mapper.toItemResponse(saved)).thenReturn(buildResponse(BigDecimal.ZERO));

            CreateInventoryItemRequest req = new CreateInventoryItemRequest();
            req.setItemName("Rice Straw");
            req.setItemType(ItemType.FEED);
            req.setUnit(UnitType.KG);

            InventoryItemResponse result = service.create(req);

            assertThat(result.getItemType()).isEqualTo("FEED");
            assertThat(result.getQuantity()).isEqualByComparingTo(BigDecimal.ZERO);
            verify(repository).save(any(InventoryItem.class));
        }
    }

    // ── FindById ─────────────────────────────────────────────────────────────────

    @Nested @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("existing item → returns response")
        void found() {
            InventoryItem item = buildItem(new BigDecimal("100.00"));
            when(repository.findByIdAndDeletedFalse(itemId)).thenReturn(Optional.of(item));
            when(mapper.toItemResponse(item)).thenReturn(buildResponse(new BigDecimal("100.00")));

            InventoryItemResponse result = service.findById(itemId);
            assertThat(result.getId()).isEqualTo(itemId);
        }

        @Test
        @DisplayName("missing item → throws ResourceNotFoundException")
        void notFound() {
            when(repository.findByIdAndDeletedFalse(itemId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.findById(itemId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    // ── Update ───────────────────────────────────────────────────────────────────

    @Nested @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("partial update → only non-null fields change")
        void partialUpdate() {
            InventoryItem item = buildItem(new BigDecimal("100.00"));
            when(repository.findByIdAndDeletedFalse(itemId)).thenReturn(Optional.of(item));
            when(repository.save(item)).thenReturn(item);
            when(mapper.toItemResponse(item)).thenReturn(buildResponse(new BigDecimal("100.00")));

            UpdateInventoryItemRequest req = new UpdateInventoryItemRequest();
            req.setReorderLevel(new BigDecimal("75.00"));

            service.update(itemId, req);

            assertThat(item.getReorderLevel()).isEqualByComparingTo("75.00");
            assertThat(item.getItemName()).isEqualTo("Rice Straw"); // unchanged
        }
    }

    // ── Delete ───────────────────────────────────────────────────────────────────

    @Nested @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("existing item → sets deleted=true")
        void softDeletes() {
            InventoryItem item = buildItem(new BigDecimal("100.00"));
            when(repository.findByIdAndDeletedFalse(itemId)).thenReturn(Optional.of(item));

            service.delete(itemId);

            assertThat(item.isDeleted()).isTrue();
            verify(repository).save(item);
        }

        @Test
        @DisplayName("missing item → throws ResourceNotFoundException")
        void notFound_throws() {
            when(repository.findByIdAndDeletedFalse(itemId)).thenReturn(Optional.empty());
            assertThatThrownBy(() -> service.delete(itemId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
