package com.farm2home.customer.domain.entity;

import com.farm2home.customer.domain.enums.ConsentPurpose;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/** One row per customer+purpose, upserted (never deleted) - "granted" flips true/false as the
 *  customer opts in/out, and createdAt/updatedAt plus the platform's existing AuditLogService
 *  (see @Audited on ConsentServiceImpl) are what prove *when* consent was given or withdrawn if
 *  ever challenged. Deleting this row would destroy exactly the evidence DPDP requires a data
 *  fiduciary to be able to produce, so unlike every other entity in this service there is
 *  deliberately no `deleted` flag here. */
@Entity
@Table(name = "consent_records", schema = "customer")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class ConsentRecord {

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "purpose", nullable = false, length = 40)
    private ConsentPurpose purpose;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    // Free-text version tag of the Privacy Notice/consent copy in force when this row was last
    // written (e.g. "privacy-notice-v1-DRAFT") - lets a future notice update be distinguished from
    // consent given under an older version. Populated by the caller (frontend sends a constant for
    // now - see consentService.ts); not enforced/validated server-side.
    @Column(name = "notice_version", length = 50)
    private String noticeVersion;

    // Where this consent state was captured (e.g. "REGISTRATION", "SETTINGS") - for audit context
    // only, not used in any authorization decision.
    @Column(name = "source", length = 50)
    private String source;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "created_at", updatable = false)
    @CreatedDate
    private LocalDateTime createdAt;

    @Column(name = "created_by", updatable = false)
    @CreatedBy
    private String createdBy;

    @Column(name = "updated_at")
    @LastModifiedDate
    private LocalDateTime updatedAt;

    @Column(name = "updated_by")
    @LastModifiedBy
    private String updatedBy;
}
