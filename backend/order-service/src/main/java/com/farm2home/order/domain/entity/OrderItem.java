package com.farm2home.order.domain.entity;

import com.farm2home.order.domain.enums.MilkType;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "order_items", schema = "`order`")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    // Exactly one of milkType/productId is set - never both, never neither (see the DB CHECK
    // constraint added in V4). milkType is the original/legacy shape (subscriptions and manual
    // orders created before Product-based ordering existed); productId is a plain UUID reference
    // into inventory-service's Product table, resolved and price-snapshotted by
    // OrderServiceImpl.createManualOrder() at order time - never trusted from the client.
    @Enumerated(EnumType.STRING)
    @Column(name = "milk_type", length = 50)
    private MilkType milkType;

    @Column(name = "product_id")
    private UUID productId;

    @Column(name = "product_name", length = 150)
    private String productName;

    @Column(name = "quantity", nullable = false, precision = 5, scale = 2)
    private BigDecimal quantity;

    @Column(name = "unit_price", nullable = false, precision = 8, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "total_price", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalPrice;

    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (totalPrice == null && quantity != null && unitPrice != null) {
            totalPrice = quantity.multiply(unitPrice);
        }
    }
}
