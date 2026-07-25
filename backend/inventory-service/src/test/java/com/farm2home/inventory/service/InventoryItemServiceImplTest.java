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
import static org.mockito.ArgumentMatchers.eq;
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
            when(mapper.toEntity(any(CreateInventoryItemRequest.class))).thenReturn(new InventoryItem());
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

    // ── FindAll / FindLowStock ──────────────────────────────────────────────────

    @Nested @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("no type filter → queries all")
        void noFilter_queriesAll() {
            InventoryItem item = buildItem(new BigDecimal("100.00"));
            when(repository.findAllByDeletedFalse(any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(item)));
            when(mapper.toItemResponse(item)).thenReturn(buildResponse(new BigDecimal("100.00")));

            assertThat(service.findAll(null, org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                    .isEqualTo(1);
            verify(repository, never()).findAllByItemTypeAndDeletedFalse(any(), any());
        }

        @Test
        @DisplayName("type filter set → queries by type")
        void withFilter_queriesByType() {
            InventoryItem item = buildItem(new BigDecimal("100.00"));
            when(repository.findAllByItemTypeAndDeletedFalse(eq(ItemType.FEED), any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(item)));
            when(mapper.toItemResponse(item)).thenReturn(buildResponse(new BigDecimal("100.00")));

            assertThat(service.findAll(ItemType.FEED, org.springframework.data.domain.Pageable.unpaged())
                    .getTotalElements()).isEqualTo(1);
        }
    }

    @Nested @DisplayName("findLowStock()")
    class FindLowStock {

        @Test
        @DisplayName("returns mapped list")
        void returnsMappedList() {
            InventoryItem item = buildItem(new BigDecimal("10.00"));
            when(repository.findItemsBelowReorderLevel()).thenReturn(java.util.List.of(item));
            when(mapper.toItemResponse(item)).thenReturn(buildResponse(new BigDecimal("10.00")));

            assertThat(service.findLowStock()).hasSize(1);
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

            doAnswer(inv -> {
                UpdateInventoryItemRequest r = inv.getArgument(0);
                InventoryItem target = inv.getArgument(1);
                if (org.springframework.util.StringUtils.hasText(r.getItemName())) target.setItemName(r.getItemName());
                if (r.getReorderLevel() != null) target.setReorderLevel(r.getReorderLevel());
                if (r.getUnitPrice() != null) target.setUnitPrice(r.getUnitPrice());
                if (org.springframework.util.StringUtils.hasText(r.getSupplier())) target.setSupplier(r.getSupplier());
                return null;
            }).when(mapper).updateItemFromRequest(eq(req), eq(item));

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
