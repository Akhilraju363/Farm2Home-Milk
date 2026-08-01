package com.farm2home.payment.scheduler;

import com.farm2home.payment.domain.repository.PaymentRepository;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.payment.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Field;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentReconciliationJobTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private PaymentService paymentService;

    private PaymentReconciliationJob newJob() throws Exception {
        PaymentReconciliationJob job = new PaymentReconciliationJob(paymentRepository, paymentService);
        Field field = PaymentReconciliationJob.class.getDeclaredField("staleAfterMinutes");
        field.setAccessible(true);
        field.set(job, 5L);
        return job;
    }

    @Nested
    @DisplayName("reconcilePendingPayments()")
    class ReconcilePendingPayments {

        @Test
        @DisplayName("no stale payments → never calls syncStatus")
        void noStalePayments_noop() throws Exception {
            when(paymentRepository.findStalePendingGatewayPaymentIds(any())).thenReturn(List.of());

            newJob().reconcilePendingPayments();

            verify(paymentService, never()).syncStatus(any());
        }

        @Test
        @DisplayName("stale payments found → syncs each one via PaymentService")
        void stalePayments_syncsEach() throws Exception {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            when(paymentRepository.findStalePendingGatewayPaymentIds(any())).thenReturn(List.of(id1, id2));
            when(paymentService.syncStatus(any())).thenReturn(PaymentResponse.builder().build());

            newJob().reconcilePendingPayments();

            verify(paymentService).syncStatus(eq(id1));
            verify(paymentService).syncStatus(eq(id2));
        }

        @Test
        @DisplayName("one payment's sync throws → the rest are still processed")
        void oneFailure_othersStillProcessed() throws Exception {
            UUID id1 = UUID.randomUUID();
            UUID id2 = UUID.randomUUID();
            when(paymentRepository.findStalePendingGatewayPaymentIds(any())).thenReturn(List.of(id1, id2));
            when(paymentService.syncStatus(id1)).thenThrow(new RuntimeException("gateway unreachable"));
            when(paymentService.syncStatus(id2)).thenReturn(PaymentResponse.builder().build());

            newJob().reconcilePendingPayments();

            verify(paymentService, times(1)).syncStatus(id1);
            verify(paymentService, times(1)).syncStatus(id2);
        }
    }
}
