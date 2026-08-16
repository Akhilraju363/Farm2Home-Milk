package com.farm2home.customer.service.impl;

import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.Audited;
import com.farm2home.customer.domain.entity.ConsentRecord;
import com.farm2home.customer.domain.enums.ConsentPurpose;
import com.farm2home.customer.domain.repository.ConsentRecordRepository;
import com.farm2home.customer.dto.request.ConsentBulkUpdateRequest;
import com.farm2home.customer.dto.request.ConsentUpdateRequest;
import com.farm2home.customer.dto.response.ConsentResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/** Self-service consent capture/withdrawal - every method is scoped to the caller's own
 *  customerId (from the bearer token), matching CustomerServiceImpl.addAddress's convention.
 *  There is deliberately no admin/scoped counterpart yet (unlike addresses) - see
 *  DPDP_PROGRESS.md "Open items". */
@Service
@RequiredArgsConstructor
public class ConsentServiceImpl {

    private final ConsentRecordRepository repository;

    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "ConsentRecord")
    public List<ConsentResponse> upsert(UUID customerId, ConsentBulkUpdateRequest request, String ipAddress, String userAgent, String source) {
        return request.getConsents().stream()
                .map(c -> upsertOne(customerId, c, ipAddress, userAgent, source))
                .map(this::toResponse)
                .toList();
    }

    private ConsentRecord upsertOne(UUID customerId, ConsentUpdateRequest c, String ipAddress, String userAgent, String source) {
        ConsentRecord record = repository.findByCustomerIdAndPurpose(customerId, c.getPurpose())
                .orElseGet(() -> ConsentRecord.builder().customerId(customerId).purpose(c.getPurpose()).build());
        record.setGranted(Boolean.TRUE.equals(c.getGranted()));
        record.setNoticeVersion(c.getNoticeVersion());
        record.setIpAddress(ipAddress);
        record.setUserAgent(userAgent);
        record.setSource(source);
        return repository.save(record);
    }

    @Transactional(readOnly = true)
    public List<ConsentResponse> findAll(UUID customerId) {
        return repository.findAllByCustomerId(customerId).stream().map(this::toResponse).toList();
    }

    /** Withdrawing is just granted=false on the same purpose - "as easy to withdraw as to give"
     *  (DPDP s.6(4)) means this is the same single-call shape as granting, not a separate flow. */
    @Transactional
    @Audited(action = AuditAction.UPDATE, entityType = "ConsentRecord")
    public ConsentResponse setOne(UUID customerId, ConsentPurpose purpose, boolean granted, String ipAddress, String userAgent) {
        ConsentRecord record = repository.findByCustomerIdAndPurpose(customerId, purpose)
                .orElseGet(() -> ConsentRecord.builder().customerId(customerId).purpose(purpose).build());
        record.setGranted(granted);
        record.setIpAddress(ipAddress);
        record.setUserAgent(userAgent);
        record.setSource("SETTINGS");
        return toResponse(repository.save(record));
    }

    private ConsentResponse toResponse(ConsentRecord r) {
        return ConsentResponse.builder()
                .purpose(r.getPurpose())
                .granted(r.isGranted())
                .noticeVersion(r.getNoticeVersion())
                .createdAt(r.getCreatedAt())
                .updatedAt(r.getUpdatedAt())
                .build();
    }
}
