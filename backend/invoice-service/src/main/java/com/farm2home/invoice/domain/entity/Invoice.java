package com.farm2home.invoice.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * A generated billing document snapshotting one order's amount at the moment an admin generated
 * it. Deliberately minimal: no tax/deliveryCharge/discount columns exist because no such concept
 * exists anywhere in the backend (Order/Payment/Subscription/Delivery/Config all have zero tax or
 * delivery-charge fields - see the Phase 1 backend audit) - subtotal and totalAmount are equal for
 * every invoice until a genuine tax/delivery-charge model is designed. No invoiceStatus column
 * either: an invoice has no independent lifecycle here, it is a point-in-time snapshot; the UI
 * reads the *order's* real status and the *payment's* real status alongside it instead of this
 * entity inventing its own.
 *
 * Customer/order/payment/farm details are deliberately NOT duplicated onto this entity beyond the
 * ids needed to look them up - InvoiceServiceImpl composes the full response by calling
 * order-service/payment-service/customer-service at read time (see the client package), per the
 * project's "do not duplicate data across services unless already snapshotted" convention.
 */
@Entity
@Table(name = "invoices", schema = "invoice")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "invoice_number", nullable = false, unique = true, length = 30)
    private String invoiceNumber;

    /** One invoice per order - enforced by a unique constraint, not just an application check,
     *  so a double-click/retry on "Generate Invoice" can never silently create two. */
    @Column(name = "order_id", nullable = false, unique = true)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "subtotal", nullable = false, precision = 10, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "total_amount", nullable = false, precision = 10, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @CreatedDate
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @CreatedBy
    @Column(name = "created_by", updatable = false, length = 100)
    private String createdBy;

    @LastModifiedDate
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    @Column(name = "is_deleted")
    @Builder.Default
    private boolean deleted = false;
}
