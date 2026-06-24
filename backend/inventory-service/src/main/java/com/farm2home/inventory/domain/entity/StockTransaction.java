package com.farm2home.inventory.domain.entity;

import com.farm2home.inventory.domain.enums.TxnType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "stock_transactions", schema = "inventory")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class StockTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private InventoryItem item;

    @Enumerated(EnumType.STRING)
    @Column(name = "txn_type", nullable = false, length = 10)
    private TxnType txnType;

    @Column(nullable = false, precision = 10, scale = 2)
    private BigDecimal quantity;

    @Column(length = 100)
    private String reason;

    @Column(name = "reference_id")
    private UUID referenceId;

    @Column(name = "transacted_at", nullable = false)
    @Builder.Default
    private LocalDateTime transactedAt = LocalDateTime.now();

    @Column(name = "created_by", length = 100)
    private String createdBy;
}
