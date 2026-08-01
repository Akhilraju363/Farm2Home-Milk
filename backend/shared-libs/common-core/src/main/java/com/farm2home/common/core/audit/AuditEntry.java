package com.farm2home.common.core.audit;

import lombok.Builder;
import lombok.Getter;

/**
 * Input to {@link AuditLogService#record(AuditEntry)}. Everything the caller doesn't know
 * (correlation id, service name, HTTP context, timestamp) is filled in by AuditLogService
 * itself, so call sites only ever supply what's specific to the operation they're auditing.
 */
@Getter
@Builder
public class AuditEntry {

    private final String action;
    private final String entityType;
    private final String entityId;
    private final String userId;
    private final String username;
    private final String oldValue;
    private final String newValue;
    private final String details;

    @Builder.Default
    private final boolean success = true;

    private final String failureReason;
}
