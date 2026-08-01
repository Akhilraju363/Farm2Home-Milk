package com.farm2home.payment.scheduler;

import com.farm2home.payment.domain.repository.PaymentRepository;
import com.farm2home.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Payment Status Synchronization: a safety net for gateway payments whose webhook was never
 * delivered (or delivered while this service was down) and whose customer never returned to call
 * {@code verify()} either — e.g. they paid successfully but closed the browser tab before the
 * checkout widget's callback fired. Every run re-checks each stale PENDING gateway payment
 * against the gateway itself via {@link PaymentService#syncStatus}, reusing the exact same
 * status-transition/event-publishing logic {@code verify()} and the webhook handler use, so this
 * job never duplicates that logic - it only decides *which* payments to check.
 *
 * Runs unconditionally under the mock provider too: {@code MockPaymentGatewayProvider} always
 * reports UNKNOWN, so every check here is a harmless no-op in local dev (mirrors the pre-gateway
 * behavior where a PENDING payment only ever moved via a manual callback).
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(name = "payment.gateway.reconciliation.enabled", havingValue = "true", matchIfMissing = true)
public class PaymentReconciliationJob {

    private final PaymentRepository paymentRepository;
    private final PaymentService paymentService;

    @Value("${payment.gateway.reconciliation.stale-after-minutes:5}")
    private long staleAfterMinutes;

    @Scheduled(fixedDelayString = "${payment.gateway.reconciliation.interval-ms:300000}")
    public void reconcilePendingPayments() {
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(staleAfterMinutes);
        List<UUID> staleIds = paymentRepository.findStalePendingGatewayPaymentIds(cutoff);
        if (staleIds.isEmpty()) {
            return;
        }

        log.info("Reconciling {} stale PENDING gateway payment(s)", staleIds.size());
        for (UUID id : staleIds) {
            try {
                paymentService.syncStatus(id);
            } catch (Exception ex) {
                // One payment's gateway lookup failing (e.g. transient network error) must never
                // stop the rest of the batch from being checked.
                log.warn("Reconciliation failed for payment {}: {}", id, ex.getMessage());
            }
        }
    }
}
