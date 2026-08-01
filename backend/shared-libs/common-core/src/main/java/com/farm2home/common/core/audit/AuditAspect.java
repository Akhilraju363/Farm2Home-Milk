package com.farm2home.common.core.audit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Generic audit recording for methods annotated @Audited - covers the common create/update/
 * delete/upload shape so individual services don't hand-write an AuditLogService.record(...)
 * call for every simple CRUD operation. Entity id is resolved from the return value's getId()
 * (works for the XxxResponse DTOs every service already returns) or, for void methods like
 * delete(UUID id), from the first UUID-typed argument.
 *
 * Exceptions are recorded as a failed audit entry and always rethrown unchanged - auditing must
 * never swallow or alter business-layer error handling.
 */
@Aspect
class AuditAspect {

    private final AuditLogService auditLogService;

    AuditAspect(AuditLogService auditLogService) {
        this.auditLogService = auditLogService;
    }

    @Around("@annotation(audited)")
    public Object around(ProceedingJoinPoint joinPoint, Audited audited) throws Throwable {
        Object[] args = joinPoint.getArgs();
        try {
            Object result = joinPoint.proceed();
            auditLogService.record(AuditEntry.builder()
                    .action(audited.action())
                    .entityType(audited.entityType())
                    .entityId(resolveEntityId(result, args))
                    .success(true)
                    .build());
            return result;
        } catch (Throwable ex) {
            auditLogService.record(AuditEntry.builder()
                    .action(audited.action())
                    .entityType(audited.entityType())
                    .entityId(resolveEntityId(null, args))
                    .success(false)
                    .failureReason(ex.getMessage())
                    .build());
            throw ex;
        }
    }

    private String resolveEntityId(Object result, Object[] args) {
        String fromResult = tryGetId(result);
        if (fromResult != null) {
            return fromResult;
        }
        for (Object arg : args) {
            if (arg instanceof UUID uuid) {
                return uuid.toString();
            }
        }
        return null;
    }

    private String tryGetId(Object target) {
        if (target == null) {
            return null;
        }
        try {
            Method getId = target.getClass().getMethod("getId");
            Object id = getId.invoke(target);
            return id != null ? id.toString() : null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}
