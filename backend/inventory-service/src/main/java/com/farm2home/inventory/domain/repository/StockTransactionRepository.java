package com.farm2home.inventory.domain.repository;

import com.farm2home.inventory.domain.entity.StockTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StockTransactionRepository extends JpaRepository<StockTransaction, UUID> {

    Page<StockTransaction> findAllByItemIdOrderByTransactedAtDesc(UUID itemId, Pageable pageable);

    Optional<StockTransaction> findByIdAndItemId(UUID id, UUID itemId);
}
