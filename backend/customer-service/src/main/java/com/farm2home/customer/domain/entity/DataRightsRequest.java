package com.farm2home.customer.domain.entity;

import com.farm2home.customer.domain.enums.DataRightsRequestStatus;
import com.farm2home.customer.domain.enums.DataRightsRequestType;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/** A data-principal rights request (access/correction/erasure/withdraw-consent) or general
 *  grievance. customerId is nullable and requesterName/requesterContact are captured directly in
 *  the request, because DPDP's grievance-redressal channel must be reachable by a data principal
 *  who never created an account (e.g. objecting to being contacted) - see
 *  DataRightsRequestController, where POST is intentionally public. Never soft-deleted: this row
 *  IS the compliance evidence that a request was received and how it was handled. */
@Entity
@Table(name = "data_rights_requests", schema = "customer")
@EntityListeners(AuditingEntityListener.class)
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
@EqualsAndHashCode(of = "id")
public class DataRightsRequest {

    @Id
    @GeneratedValue
    private UUID id;

    // Set only when the submitter was logged in at submission time - never required, never trusted
    // as the sole identity check (requesterContact is always captured too, since an account may no
    // longer exist by the time the request is actioned).
    @Column(name = "customer_id")
    private UUID customerId;

    @Column(name = "requester_name", nullable = false, length = 150)
    private String requesterName;

    @Column(name = "requester_contact", nullable = false, length = 150)
    private String requesterContact;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 30)
    private DataRightsRequestType requestType;

    @Column(name = "details", columnDefinition = "TEXT")
    private String details;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @Builder.Default
    private DataRightsRequestStatus status = DataRightsRequestStatus.NEW;

    @Column(name = "resolution_notes", columnDefinition = "TEXT")
    private String resolutionNotes;

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
