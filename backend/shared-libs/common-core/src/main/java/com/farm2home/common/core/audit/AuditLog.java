package com.farm2home.common.core.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * One row per audited event (login, logout, payment, order/status changes, entity
 * created/updated, ...). Deliberately flat and entity-agnostic - entityType/entityId identify
 * whatever the event is about, action is a free-form but consistent code (see AuditAction),
 * and details carries a short human-readable summary rather than a structured payload, since
 * this is read by people investigating "who did what and when", not machine-parsed.
 */
@Entity
@Table(name = "audit_log")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, updatable = false)
    private OffsetDateTime occurredAt;

    @Column(nullable = false, length = 50, updatable = false)
    private String action;

    @Column(length = 100, updatable = false)
    private String entityType;

    @Column(length = 100, updatable = false)
    private String entityId;

    @Column(length = 100, updatable = false)
    private String performedBy;

    @Column(length = 64, updatable = false)
    private String correlationId;

    @Column(columnDefinition = "TEXT", updatable = false)
    private String details;
}
