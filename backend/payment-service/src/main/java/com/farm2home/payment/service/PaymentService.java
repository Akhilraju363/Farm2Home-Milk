package com.farm2home.payment.service;

import com.farm2home.payment.domain.enums.PaymentStatus;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.request.VerifyPaymentRequest;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.PaymentAnalyticsPoint;
import com.farm2home.common.core.analytics.RevenueTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.dashboard.PaymentSummaryResponse;
import com.farm2home.common.core.reports.PaymentReportRow;
import com.farm2home.common.core.reports.PaymentReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.export.ExportFormat;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface PaymentService {

    /** Creates a payment attempt. For UPI/RAZORPAY methods this also creates an order on the
     *  active {@link com.farm2home.payment.gateway.PaymentGatewayProvider} and returns a public
     *  checkout key in the response - the payment stays PENDING until {@link #verify} or a
     *  webhook confirms it. WALLET and CASH are unchanged: instant SUCCESS / instant PENDING
     *  respectively, no gateway involved.
     *
     *  The payer and amount always come from the order itself (order-service): the request's
     *  amount must match the order total, and its customerId (admin-only use) must match the
     *  order's customer. A retried online payment for an order that already has a PENDING online
     *  payment resumes that payment's gateway order instead of creating a second one. */
    PaymentResponse initiate(InitiatePaymentRequest request, UUID callerId, boolean isAdmin);

    /** Manual/legacy status update by payment reference - kept for backward compatibility and as
     *  a way to simulate gateway outcomes under the mock provider (local dev/tests). Real gateway
     *  outcomes should arrive via {@link #verify} (client-driven) or the webhook endpoint
     *  (server-to-server), not this method. */
    PaymentResponse processCallback(PaymentCallbackRequest request);

    /** Verifies a client-reported checkout completion against the gateway (signature check, plus
     *  a confirmatory status fetch where the provider supports it). An invalid signature is
     *  rejected without changing the payment; a gateway-confirmed capture marks it SUCCESS, a
     *  gateway-confirmed failure marks it FAILED, and anything else leaves it PENDING. Idempotent:
     *  repeating a verify that already settled the payment returns its current state. */
    PaymentResponse verify(UUID paymentId, VerifyPaymentRequest request, UUID customerId, boolean isAdmin);

    /** Processes an asynchronous gateway webhook notification. Verifies the payload's signature
     *  before acting on it and is safe to call repeatedly for the same event (gateways routinely
     *  retry webhook delivery) - an event that doesn't change the payment's state is a no-op. */
    void handleWebhook(String rawPayload, String signatureHeader);

    /** Re-fetches this payment's current status directly from the gateway and applies it if it
     *  differs from our local record. A no-op for anything not PENDING-and-gateway-backed. Used
     *  by the scheduled reconciliation job and available for manual/admin-triggered resync. */
    PaymentResponse syncStatus(UUID paymentId);

    PaymentResponse refund(UUID paymentId, UUID customerId, boolean isAdmin);

    PaymentResponse findById(UUID id, UUID customerId, boolean isAdmin);

    List<PaymentResponse> findByOrderId(UUID orderId, UUID customerId, boolean isAdmin);

    Page<PaymentResponse> findAll(UUID customerId, boolean isAdmin, Pageable pageable);

    PaymentSummaryResponse getSummary();

    ReportPage<PaymentReportRow, PaymentReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            PaymentStatus status, UUID customerId, Pageable pageable);

    /** Streams payments matching the same filters as {@link #getReport} to a downloadable file.
     *  See PaymentServiceImpl for why this isn't @Transactional. */
    void export(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo, PaymentStatus status,
            UUID customerId, String sortBy, boolean ascending) throws IOException;

    /** Revenue Trend: SUM of SUCCESS payment amounts bucketed by the requested granularity. */
    TrendSeries<RevenueTrendPoint> getRevenueTrend(Granularity granularity, LocalDate dateFrom, LocalDate dateTo);

    /** Payment Analytics: transaction volume and success/failure mix bucketed by the requested
     *  granularity - independent of status, unlike Revenue Trend. */
    TrendSeries<PaymentAnalyticsPoint> getPaymentAnalytics(Granularity granularity, LocalDate dateFrom, LocalDate dateTo);

    /** Lightweight existence check (SUCCESS or PENDING payment) for cross-service callers that
     *  only need a yes/no signal, not payment details - e.g. delivery-service verifying payment
     *  progress before dispatching an order. Deliberately not scoped to the order's own customer
     *  (see the controller endpoint's Javadoc for why). */
    boolean hasPayableProgress(UUID orderId);
}
