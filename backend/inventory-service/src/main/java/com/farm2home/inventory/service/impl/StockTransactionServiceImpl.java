package com.farm2home.inventory.service.impl;

import com.farm2home.inventory.domain.entity.InventoryItem;
import com.farm2home.inventory.domain.entity.StockTransaction;
import com.farm2home.inventory.domain.enums.TxnType;
import com.farm2home.inventory.domain.repository.InventoryItemRepository;
import com.farm2home.inventory.domain.repository.StockTransactionRepository;
import com.farm2home.inventory.dto.request.StockTransactionRequest;
import com.farm2home.inventory.dto.response.StockTransactionResponse;
import com.farm2home.inventory.exception.InventoryException;
import com.farm2home.inventory.mapper.InventoryMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockTransactionServiceImpl {

    private final StockTransactionRepository txnRepository;
    private final InventoryItemRepository itemRepository;
    private final InventoryItemServiceImpl itemService;
    private final InventoryMapper mapper;

    @Transactional
    public StockTransactionResponse transact(UUID itemId, StockTransactionRequest request) {
        InventoryItem item = itemService.getItem(itemId);

        if (request.getTxnType() == TxnType.OUT) {
            if (item.getQuantity().compareTo(request.getQuantity()) < 0) {
                throw new InventoryException("Insufficient stock for item " + itemId
                        + ". Available: " + item.getQuantity() + ", requested: " + request.getQuantity());
            }
            item.setQuantity(item.getQuantity().subtract(request.getQuantity()));
        } else {
            item.setQuantity(item.getQuantity().add(request.getQuantity()));
        }
        itemRepository.save(item);

        StockTransaction txn = StockTransaction.builder()
                .item(item)
                .txnType(request.getTxnType())
                .quantity(request.getQuantity())
                .reason(request.getReason())
                .referenceId(request.getReferenceId())
                .createdBy(currentUser())
                .build();

        return mapper.toTxnResponse(txnRepository.save(txn));
    }

    @Transactional(readOnly = true)
    public Page<StockTransactionResponse> findByItem(UUID itemId, Pageable pageable) {
        itemService.getItem(itemId); // verify exists
        return txnRepository.findAllByItemIdOrderByTransactedAtDesc(itemId, pageable)
                .map(mapper::toTxnResponse);
    }

    private String currentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return "system";
        Object principal = auth.getPrincipal();
        if (principal instanceof com.farm2home.inventory.config.UserPrincipal up) return up.mobile();
        return auth.getName();
    }
}
