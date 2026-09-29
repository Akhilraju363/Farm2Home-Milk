package com.farm2home.payment.service.impl;

import com.farm2home.common.core.constants.EmailTemplateConstants;
import com.farm2home.common.core.dashboard.PaymentSummaryResponse;
import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.enums.PaymentMethod;
import com.farm2home.payment.domain.enums.PaymentStatus;
import com.farm2home.payment.domain.repository.PaymentRepository;
import com.farm2home.payment.domain.repository.PaymentSpecifications;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.request.VerifyPaymentRequest;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.payment.event.PaymentStatusChangedEvent;
import com.farm2home.payment.exception.PaymentException;
import com.farm2home.payment.exception.ResourceNotFoundException;
import com.farm2home.payment.gateway.GatewayOrder;
import com.farm2home.payment.gateway.GatewayOrderRequest;
import com.farm2home.payment.gateway.GatewayPaymentState;
import com.farm2home.payment.gateway.GatewayPaymentStatus;
import com.farm2home.payment.gateway.GatewayPaymentVerification;
import com.farm2home.payment.gateway.GatewayRefund;
import com.farm2home.payment.gateway.GatewayRefundRequest;
import com.farm2home.payment.gateway.GatewayVerificationRequest;
import com.farm2home.payment.gateway.GatewayWebhookEvent;
import com.farm2home.payment.gateway.PaymentGatewayProvider;
import com.farm2home.payment.mapper.PaymentMapper;
import com.farm2home.payment.service.PaymentService;
import com.farm2home.payment.service.WalletService;
import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.PaymentAnalyticsPoint;
import com.farm2home.common.core.analytics.RevenueTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditLogService;
import com.farm2home.common.core.reports.PaymentReportRow;
import com.farm2home.common.core.reports.PaymentReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.export.BatchSupplier;
import com.farm2home.common.export.ExportColumn;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.export.TabularExporterFactory;
import com.farm2home.payment.client.OrderServiceClient;
import com.farm2home.payment.client.OrderStatusResponse;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final WalletService walletService;
    private final PaymentMapper mapper;
    private final ApplicationEventPublisher eventPublisher;
    private final AuditLogService auditLogService;
    private final EntityManager entityManager;
    private final OrderServiceClient orderServiceClient;
    private final PaymentGatewayProvider gatewayProvider;

    /** Online (gateway-backed) methods - UPI is kept for existing records and API callers, but both
     *  go through the same configured gateway provider and the same Checkout flow. */
    private static final List<PaymentMethod> GATEWAY_METHODS = List.of(PaymentMethod.RAZORPAY, PaymentMethod.UPI);

    @Override
    @Transactional
    public PaymentResponse initiate(InitiatePaymentRequest request, UUID callerId, boolean isAdmin) {
        OrderStatusResponse order = verifyOrderIsPayable(request.getOrderId());
        UUID resolvedCustomerId = resolvePayer(order, request, callerId, isAdmin);
        BigDecimal amount = resolveAmount(order, request);

        if (isGatewayMethod(request.getPaymentMethod())) {
            PaymentResponse resumed = resumePendingGatewayPayment(request.getOrderId(), resolvedCustomerId, amount);
            if (resumed != null) {
                return resumed;
            }
        }

        // Reject if a payment already exists for this order that succeeded or is still in
        // flight - only a prior FAILED/REFUNDED attempt leaves the order payable again.
        if (paymentRepository.existsByOrderIdAndPaymentStatusInAndDeletedFalse(
                request.getOrderId(), List.of(PaymentStatus.SUCCESS, PaymentStatus.PENDING))) {
            throw new PaymentException("A payment is already in progress or completed for order " + request.getOrderId());
        }

        String reference = "PAY-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = mapper.toEntity(request);
        payment.setCustomerId(resolvedCustomerId);
        payment.setAmount(amount);
        payment.setPaymentReference(reference);
        payment.setPaymentStatus(PaymentStatus.PENDING);

        if (request.getPaymentMethod() == PaymentMethod.WALLET) {
            // Debit wallet immediately — if insufficient balance, exception is thrown before saving
            walletService.debitForPayment(resolvedCustomerId, amount, null);
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            Payment saved = paymentRepository.save(payment);
            // Update wallet transaction with the actual payment ID
            log.debug("Wallet payment processed for order {}", request.getOrderId());
            publishStatusChanged(saved, EmailTemplateConstants.EVENT_PAYMENT_SUCCESS);
            auditLogService.record(AuditAction.PAYMENT, "Payment", saved.getId().toString(),
                    resolvedCustomerId.toString(),
                    "Wallet payment " + reference + " succeeded for order " + request.getOrderId());
            return mapper.toResponse(saved);
        }

        if (request.getPaymentMethod() == PaymentMethod.CASH) {
            // Cash on delivery — stays PENDING until delivery staff marks it paid
            Payment saved = paymentRepository.save(payment);
            log.debug("COD payment initiated for order {}", request.getOrderId());
            auditLogService.record(AuditAction.PAYMENT, "Payment", saved.getId().toString(),
                    resolvedCustomerId.toString(),
                    "Cash-on-delivery payment " + reference + " initiated for order " + request.getOrderId());
            return mapper.toResponse(saved);
        }

        // UPI / RAZORPAY — create a gateway order for the server-resolved amount; stays PENDING
        // until verify() or a webhook confirms it (or the reconciliation job catches a missed one)
        GatewayOrder gatewayOrder = gatewayProvider.createOrder(
                new GatewayOrderRequest(reference, amount, "Order " + request.getOrderId()));
        payment.setGatewayOrderId(gatewayOrder.gatewayOrderId());
        Payment saved = paymentRepository.save(payment);
        log.debug("Gateway payment {} initiated via {} for order {}", reference, gatewayProvider.getName(),
                request.getOrderId());
        auditLogService.record(AuditAction.PAYMENT, "Payment", saved.getId().toString(),
                resolvedCustomerId.toString(),
                "Gateway payment " + reference + " initiated for order " + request.getOrderId());

        PaymentResponse response = mapper.toResponse(saved);
        response.setGatewayCheckoutKeyId(gatewayOrder.checkoutKeyId());
        return response;
    }

    /** The payer is always the order's own customer, as reported by order-service - never a
     *  client-supplied id. A non-admin can only reach their own orders (order-service scopes the
     *  lookup by the forwarded caller identity), and this re-checks that rather than relying on
     *  it; an explicit request customerId (admin paying on a customer's behalf) must agree. */
    private UUID resolvePayer(OrderStatusResponse order, InitiatePaymentRequest request, UUID callerId, boolean isAdmin) {
        UUID orderCustomerId = order.getCustomerId();
        if (orderCustomerId == null) {
            throw new PaymentException("Could not verify the order's customer right now. Please try again.");
        }
        if (!isAdmin && !orderCustomerId.equals(callerId)) {
            throw new ResourceNotFoundException("Order not found: " + request.getOrderId());
        }
        if (request.getCustomerId() != null && !request.getCustomerId().equals(orderCustomerId)) {
            throw new PaymentException("customerId does not match the customer of order " + request.getOrderId());
        }
        return orderCustomerId;
    }

    /** The amount charged is the order's own total from order-service. The request's amount is
     *  only accepted as a confirmation of what the customer was shown - a mismatch is rejected
     *  rather than silently corrected, so a stale or tampered checkout can't pay the wrong sum. */
    private BigDecimal resolveAmount(OrderStatusResponse order, InitiatePaymentRequest request) {
        BigDecimal orderTotal = order.getTotalAmount();
        if (orderTotal == null || orderTotal.signum() <= 0) {
            throw new PaymentException("Could not verify the order amount right now. Please try again.");
        }
        if (request.getAmount().compareTo(orderTotal) != 0) {
            throw new PaymentException(String.format(
                    "Payment amount ₹%s does not match the order total ₹%s.", request.getAmount(), orderTotal));
        }
        return orderTotal;
    }

    /** Resumes checkout for an online payment that is still PENDING (the customer dismissed the
     *  checkout, the page reloaded, or they are retrying) by handing back the same gateway order,
     *  rather than rejecting the retry or creating a second gateway order that could also be paid. */
    private PaymentResponse resumePendingGatewayPayment(UUID orderId, UUID customerId, BigDecimal amount) {
        Payment pending = paymentRepository
                .findFirstByOrderIdAndPaymentStatusAndPaymentMethodInAndDeletedFalseOrderByCreatedAtDesc(
                        orderId, PaymentStatus.PENDING, GATEWAY_METHODS)
                .orElse(null);
        if (pending == null || pending.getGatewayOrderId() == null
                || !customerId.equals(pending.getCustomerId()) || pending.getAmount().compareTo(amount) != 0) {
            return null;
        }
        log.debug("Resuming PENDING gateway payment {} for order {}", pending.getPaymentReference(), orderId);
        PaymentResponse response = mapper.toResponse(pending);
        response.setGatewayCheckoutKeyId(gatewayProvider.checkoutKeyId());
        return response;
    }

    /** Server-side confirmation of a client-reported checkout completion. The client's report is
     *  never trusted on its own: the provider checks the signature and re-fetches the payment from
     *  the gateway, and only a gateway-confirmed capture marks the payment SUCCESS.
     *
     *  Idempotent - the payment row is locked for the duration, and a repeat call for a payment
     *  this same gateway payment already settled just returns the current state. */
    @Override
    @Transactional
    public PaymentResponse verify(UUID paymentId, VerifyPaymentRequest request, UUID customerId, boolean isAdmin) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .filter(p -> isAdmin || p.getCustomerId().equals(customerId))
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        if (!isGatewayMethod(payment.getPaymentMethod())) {
            throw new PaymentException("Payment " + payment.getPaymentReference() + " is not an online payment");
        }
        if (payment.getGatewayOrderId() == null || !payment.getGatewayOrderId().equals(request.getGatewayOrderId())) {
            throw new PaymentException("Gateway order ID does not match payment " + payment.getPaymentReference());
        }
        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS
                && request.getGatewayPaymentId().equals(payment.getGatewayPaymentId())) {
            log.debug("Duplicate verify for already-settled payment {}", payment.getPaymentReference());
            return mapper.toResponse(payment);
        }
        // PENDING is the normal case. FAILED is still verifiable: Razorpay lets the customer retry
        // inside the same checkout after a failed attempt, and a later attempt on the same gateway
        // order can genuinely capture.
        if (payment.getPaymentStatus() != PaymentStatus.PENDING && payment.getPaymentStatus() != PaymentStatus.FAILED) {
            throw new PaymentException("Payment " + payment.getPaymentReference() +
                    " is not in PENDING state (current: " + payment.getPaymentStatus() + ")");
        }
        if (paymentRepository.existsByGatewayPaymentIdAndIdNotAndDeletedFalse(request.getGatewayPaymentId(), payment.getId())) {
            throw new PaymentException("This gateway payment has already been used for a different payment.");
        }

        GatewayPaymentVerification result = gatewayProvider.verifyPayment(new GatewayVerificationRequest(
                request.getGatewayOrderId(), request.getGatewayPaymentId(), request.getSignature(), payment.getAmount()));

        if (!result.signatureValid()) {
            // A forged/garbled report changes nothing: the payment stays as it was, and the real
            // outcome still arrives via webhook or reconciliation.
            log.warn("Rejected verify for payment {}: invalid gateway signature", payment.getPaymentReference());
            throw new PaymentException("Payment verification failed: the payment signature is invalid.");
        }

        String actor = customerId != null ? customerId.toString() : payment.getCustomerId().toString();
        applyGatewayOutcome(payment, result.state(), result.gatewayPaymentId(), result.rawResponse(),
                "Gateway verification", actor);
        return mapper.toResponse(payment);
    }

    /** Idempotent: webhooks are routinely retried/duplicated by gateways, and the payment row is
     *  locked so a webhook racing a client verify() for the same payment is serialized with it.
     *  Unknown/unmatched events are logged and ignored rather than rejected, since gateways send
     *  event types this service has no reason to act on. */
    @Override
    @Transactional
    public void handleWebhook(String rawPayload, String signatureHeader) {
        GatewayWebhookEvent event = gatewayProvider.parseWebhookEvent(rawPayload, signatureHeader);

        if (event.gatewayOrderId() == null) {
            log.warn("Ignoring webhook event [{}] with no gateway order id", event.eventType());
            return;
        }

        Payment payment = paymentRepository.findByGatewayOrderIdForUpdate(event.gatewayOrderId()).orElse(null);
        if (payment == null) {
            log.warn("Ignoring webhook event [{}] for unknown gateway order {}", event.eventType(), event.gatewayOrderId());
            return;
        }
        if (event.state() == GatewayPaymentState.CAPTURED && event.amount() != null
                && event.amount().compareTo(payment.getAmount()) != 0) {
            log.warn("Ignoring webhook event [{}] for payment {}: gateway amount {} does not match expected {}",
                    event.eventType(), payment.getPaymentReference(), event.amount(), payment.getAmount());
            return;
        }

        boolean changed = applyGatewayOutcome(payment, event.state(), event.gatewayPaymentId(), event.rawPayload(),
                "Webhook [" + event.eventType() + "]", payment.getCustomerId().toString());
        if (!changed) {
            log.debug("Webhook event [{}] for payment {} left it unchanged (state {})",
                    event.eventType(), payment.getPaymentReference(), payment.getPaymentStatus());
        }
    }

    @Override
    @Transactional
    public PaymentResponse syncStatus(UUID paymentId) {
        Payment payment = paymentRepository.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + paymentId));

        if (payment.getPaymentStatus() != PaymentStatus.PENDING || !isGatewayMethod(payment.getPaymentMethod())
                || payment.getGatewayOrderId() == null) {
            return mapper.toResponse(payment);
        }

        GatewayPaymentStatus status = gatewayProvider.fetchOrderStatus(payment.getGatewayOrderId());
        applyGatewayOutcome(payment, status.state(), status.gatewayPaymentId(), status.rawResponse(),
                "Reconciliation", payment.getCustomerId().toString());
        return mapper.toResponse(payment);
    }

    /** The single place a gateway-confirmed outcome changes a payment's status, shared by
     *  verify(), the webhook and reconciliation so they can never disagree:
     *  <ul>
     *    <li>CAPTURED → SUCCESS, from PENDING or FAILED (money the gateway confirms it collected is
     *        never left recorded as failed); a payment already SUCCESS is left untouched.</li>
     *    <li>FAILED → FAILED, only from PENDING - a failure never overrides a success.</li>
     *    <li>anything else (created/authorized/unknown) → no change; the payment stays PENDING until
     *        the gateway reports a final state.</li>
     *  </ul>
     *  @return whether the payment's status changed */
    private boolean applyGatewayOutcome(Payment payment, GatewayPaymentState state, String gatewayPaymentId,
                                        String rawResponse, String source, String actor) {
        PaymentStatus current = payment.getPaymentStatus();
        PaymentStatus next;
        if (state == GatewayPaymentState.CAPTURED
                && (current == PaymentStatus.PENDING || current == PaymentStatus.FAILED)) {
            next = PaymentStatus.SUCCESS;
        } else if (state == GatewayPaymentState.FAILED && current == PaymentStatus.PENDING) {
            next = PaymentStatus.FAILED;
        } else {
            return false;
        }

        if (gatewayPaymentId != null) {
            payment.setGatewayPaymentId(gatewayPaymentId);
        }
        payment.setGatewayResponse(rawResponse);
        payment.setPaymentStatus(next);
        if (next == PaymentStatus.SUCCESS) {
            payment.setPaidAt(LocalDateTime.now());
            if (current == PaymentStatus.FAILED) {
                log.warn("Payment {} was FAILED but the gateway confirms a capture - recording SUCCESS",
                        payment.getPaymentReference());
            }
        }
        Payment saved = paymentRepository.save(payment);

        publishStatusChanged(saved, next == PaymentStatus.SUCCESS
                ? EmailTemplateConstants.EVENT_PAYMENT_SUCCESS : EmailTemplateConstants.EVENT_PAYMENT_FAILED);
        auditLogService.record(AuditAction.PAYMENT, "Payment", saved.getId().toString(), actor,
                source + ": payment " + saved.getPaymentReference() + " " + current + " -> " + next);
        return true;
    }

    private static boolean isGatewayMethod(PaymentMethod method) {
        return GATEWAY_METHODS.contains(method);
    }

    @Override
    @Transactional
    public PaymentResponse processCallback(PaymentCallbackRequest request) {
        Payment payment = paymentRepository.findByPaymentReferenceAndDeletedFalse(request.getPaymentReference())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Payment not found: " + request.getPaymentReference()));

        if (payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new PaymentException("Payment " + request.getPaymentReference() +
                    " is not in PENDING state (current: " + payment.getPaymentStatus() + ")");
        }

        payment.setGatewayResponse(request.getGatewayResponse());
        if (request.getGatewayPaymentId() != null) {
            payment.setGatewayPaymentId(request.getGatewayPaymentId());
        }

        if (Boolean.TRUE.equals(request.getSuccess())) {
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            Payment saved = paymentRepository.save(payment);
            publishStatusChanged(saved, EmailTemplateConstants.EVENT_PAYMENT_SUCCESS);
            auditLogService.record(AuditAction.PAYMENT, "Payment", saved.getId().toString(),
                    saved.getCustomerId().toString(),
                    "Gateway callback: payment " + request.getPaymentReference() + " succeeded");
            return mapper.toResponse(saved);
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            Payment saved = paymentRepository.save(payment);
            publishStatusChanged(saved, EmailTemplateConstants.EVENT_PAYMENT_FAILED);
            auditLogService.record(AuditAction.PAYMENT, "Payment", saved.getId().toString(),
                    saved.getCustomerId().toString(),
                    "Gateway callback: payment " + request.getPaymentReference() + " failed");
            return mapper.toResponse(saved);
        }
    }

    @Override
    @Transactional
    public PaymentResponse refund(UUID paymentId, UUID customerId, boolean isAdmin) {
        Payment payment = resolvePayment(paymentId, customerId, isAdmin);

        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS) {
            throw new PaymentException("Only SUCCESS payments can be refunded (current: " +
                    payment.getPaymentStatus() + ")");
        }

        if (payment.getPaymentMethod() == PaymentMethod.WALLET) {
            // Money never left the app for a wallet payment — reversing it is a wallet credit.
            walletService.creditRefund(payment.getCustomerId(), payment.getAmount(), payment.getId());
        } else if (payment.getPaymentMethod() == PaymentMethod.UPI || payment.getPaymentMethod() == PaymentMethod.RAZORPAY) {
            // Gateway-collected money is refunded back through the gateway to its original
            // source (bank/UPI/card) — not credited to the in-app wallet.
            if (payment.getGatewayPaymentId() == null) {
                throw new PaymentException("Cannot refund payment " + payment.getPaymentReference() +
                        ": no gateway payment reference recorded for it");
            }
            GatewayRefund gatewayRefund = gatewayProvider.refund(new GatewayRefundRequest(
                    payment.getGatewayPaymentId(), payment.getAmount(), payment.getPaymentReference()));
            payment.setGatewayRefundId(gatewayRefund.gatewayRefundId());
        }
        // CASH: no monetary instrument to reverse automatically - the refund is recorded as a
        // status change only, with any physical cash return handled outside this system.

        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        Payment saved = paymentRepository.save(payment);

        publishStatusChanged(saved, "PAYMENT_REFUNDED");
        auditLogService.record(AuditAction.PAYMENT, "Payment", saved.getId().toString(), customerId.toString(),
                "Payment " + saved.getPaymentReference() + " refunded" + (isAdmin ? " (admin action)" : ""));
        return mapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse findById(UUID id, UUID customerId, boolean isAdmin) {
        return mapper.toResponse(resolvePayment(id, customerId, isAdmin));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> findByOrderId(UUID orderId, UUID customerId, boolean isAdmin) {
        List<Payment> payments = paymentRepository.findAllByOrderIdAndDeletedFalse(orderId);
        if (!isAdmin) {
            payments = payments.stream()
                    .filter(p -> p.getCustomerId().equals(customerId))
                    .toList();
        }
        return payments.stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PaymentResponse> findAll(UUID customerId, boolean isAdmin, Pageable pageable) {
        if (isAdmin) return paymentRepository.findAllByDeletedFalse(pageable).map(mapper::toResponse);
        return paymentRepository.findAllByCustomerIdAndDeletedFalse(customerId, pageable).map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentSummaryResponse getSummary() {
        LocalDate today = LocalDate.now();
        LocalDateTime startOfDay = today.atStartOfDay();
        LocalDateTime startOfNextDay = today.plusDays(1).atStartOfDay();
        LocalDateTime startOfMonth = today.withDayOfMonth(1).atStartOfDay();
        LocalDateTime startOfNextMonth = today.withDayOfMonth(1).plusMonths(1).atStartOfDay();

        BigDecimal revenueToday = paymentRepository.sumAmountByStatusAndPaidAtBetween(
                PaymentStatus.SUCCESS, startOfDay, startOfNextDay);
        BigDecimal revenueThisMonth = paymentRepository.sumAmountByStatusAndPaidAtBetween(
                PaymentStatus.SUCCESS, startOfMonth, startOfNextMonth);

        return PaymentSummaryResponse.builder()
                .revenueToday(revenueToday != null ? revenueToday : BigDecimal.ZERO)
                .revenueThisMonth(revenueThisMonth != null ? revenueThisMonth : BigDecimal.ZERO)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public ReportPage<PaymentReportRow, PaymentReportSummary> getReport(LocalDate dateFrom, LocalDate dateTo,
            PaymentStatus status, UUID customerId, Pageable pageable) {
        Specification<Payment> baseSpec = buildFilterSpecification(dateFrom, dateTo, null, customerId);
        Specification<Payment> spec = buildFilterSpecification(dateFrom, dateTo, status, customerId);

        Page<Payment> page = paymentRepository.findAll(spec, pageable);
        List<PaymentReportRow> rows = page.getContent().stream()
                .map(p -> PaymentReportRow.builder()
                        .paymentId(p.getId())
                        .orderId(p.getOrderId())
                        .customerId(p.getCustomerId())
                        .amount(p.getAmount())
                        .paymentMethod(p.getPaymentMethod().name())
                        .paymentStatus(p.getPaymentStatus().name())
                        .paidAt(p.getPaidAt())
                        .createdAt(p.getCreatedAt())
                        .build())
                .toList();

        PaymentReportSummary summary = PaymentReportSummary.builder()
                .totalPayments(page.getTotalElements())
                .totalAmount(sumAmount(spec))
                .successAmount(sumAmount(baseSpec.and(PaymentSpecifications.hasStatus(PaymentStatus.SUCCESS))))
                .build();

        return ReportPage.<PaymentReportRow, PaymentReportSummary>builder()
                .content(rows)
                .pageNumber(page.getNumber())
                .pageSize(page.getSize())
                .totalElements(page.getTotalElements())
                .totalPages(page.getTotalPages())
                .summary(summary)
                .build();
    }

    private Specification<Payment> buildFilterSpecification(LocalDate dateFrom, LocalDate dateTo,
            PaymentStatus status, UUID customerId) {
        Specification<Payment> spec = Specification.where(PaymentSpecifications.notDeleted());
        if (dateFrom != null || dateTo != null) {
            spec = spec.and(PaymentSpecifications.createdBetween(dateFrom, dateTo));
        }
        if (customerId != null) {
            spec = spec.and(PaymentSpecifications.hasCustomer(customerId));
        }
        if (status != null) {
            spec = spec.and(PaymentSpecifications.hasStatus(status));
        }
        return spec;
    }

    /** Streams matching payments straight to {@code out} in the requested file format, one
     *  bounded page at a time, so exporting a very large payment history never requires holding
     *  the full result set in memory. Each batch fetch runs in its own short-lived Spring Data
     *  transaction (this method is deliberately NOT wrapped in a single @Transactional so a
     *  slow export doesn't pin one DB connection for its entire duration). Runs on the async
     *  StreamingResponseBody dispatch thread, not the original request thread. */
    @Override
    public void export(ExportFormat format, OutputStream out, LocalDate dateFrom, LocalDate dateTo,
            PaymentStatus status, UUID customerId, String sortBy, boolean ascending) throws IOException {
        Specification<Payment> spec = buildFilterSpecification(dateFrom, dateTo, status, customerId);
        Sort sort = ascending ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();

        List<ExportColumn<Payment>> columns = List.of(
                new ExportColumn<>("Payment Reference", p -> p.getPaymentReference() == null ? "" : p.getPaymentReference()),
                new ExportColumn<>("Order ID", p -> p.getOrderId().toString()),
                new ExportColumn<>("Customer ID", p -> p.getCustomerId().toString()),
                new ExportColumn<>("Amount", p -> p.getAmount().toString()),
                new ExportColumn<>("Payment Method", p -> p.getPaymentMethod().name()),
                new ExportColumn<>("Payment Status", p -> p.getPaymentStatus().name()),
                new ExportColumn<>("Paid At", p -> p.getPaidAt() == null ? "" : p.getPaidAt().toString()),
                new ExportColumn<>("Created At", p -> p.getCreatedAt() == null ? "" : p.getCreatedAt().toString()));

        BatchSupplier<Payment> supplier = (page, size) ->
                paymentRepository.findAll(spec, PageRequest.of(page, size, sort)).getContent();

        TabularExporterFactory.<Payment>forFormat(format)
                .write(out, columns.stream().map(ExportColumn::header).toList(), columns, supplier);
    }

    /** Reuses the same Specification that builds the page's WHERE clause, so the aggregate
     *  total is always computed over exactly the same filtered set - just a different SELECT. */
    private BigDecimal sumAmount(Specification<Payment> spec) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<BigDecimal> cq = cb.createQuery(BigDecimal.class);
        Root<Payment> root = cq.from(Payment.class);
        cq.select(cb.coalesce(cb.sum(root.get("amount")), BigDecimal.ZERO));
        Predicate predicate = spec.toPredicate(root, cq, cb);
        if (predicate != null) {
            cq.where(predicate);
        }
        return entityManager.createQuery(cq).getSingleResult();
    }

    /** Revenue Trend: the date_trunc GROUP BY/SUM runs entirely in Postgres (see
     *  PaymentRepository.findRevenueTrend) - this method only maps the already-aggregated,
     *  one-row-per-period result into the response DTO. */
    @Override
    @Transactional(readOnly = true)
    public TrendSeries<RevenueTrendPoint> getRevenueTrend(Granularity granularity, LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime start = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime endExclusive = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;

        List<RevenueTrendPoint> points = paymentRepository.findRevenueTrend(granularity.getSqlUnit(), start, endExclusive)
                .stream()
                .map(row -> RevenueTrendPoint.builder().period(row.getPeriod()).revenue(row.getRevenue()).build())
                .toList();

        return TrendSeries.<RevenueTrendPoint>builder()
                .granularity(granularity).from(dateFrom).to(dateTo).points(points).build();
    }

    /** Payment Analytics: PaymentRepository.findPaymentAnalytics does the real aggregation
     *  (COUNT/SUM GROUP BY period + status) in Postgres; this method only folds the handful of
     *  per-status rows for each period into one point - a linear scan over an already-tiny,
     *  already-aggregated result set, not a re-aggregation of raw payments. */
    @Override
    @Transactional(readOnly = true)
    public TrendSeries<PaymentAnalyticsPoint> getPaymentAnalytics(Granularity granularity, LocalDate dateFrom, LocalDate dateTo) {
        LocalDateTime start = dateFrom != null ? dateFrom.atStartOfDay() : null;
        LocalDateTime endExclusive = dateTo != null ? dateTo.plusDays(1).atStartOfDay() : null;

        Map<LocalDate, PaymentAnalyticsPoint> byPeriod = new LinkedHashMap<>();
        for (PaymentRepository.PaymentAnalyticsRow row : paymentRepository.findPaymentAnalytics(
                granularity.getSqlUnit(), start, endExclusive)) {
            PaymentAnalyticsPoint point = byPeriod.computeIfAbsent(row.getPeriod(), period -> PaymentAnalyticsPoint.builder()
                    .period(period).totalPayments(0).totalAmount(BigDecimal.ZERO).successCount(0).failedCount(0).build());
            point.setTotalPayments(point.getTotalPayments() + row.getTxnCount());
            point.setTotalAmount(point.getTotalAmount().add(row.getAmount()));
            if (PaymentStatus.SUCCESS.name().equals(row.getStatus())) {
                point.setSuccessCount(point.getSuccessCount() + row.getTxnCount());
            } else if (PaymentStatus.FAILED.name().equals(row.getStatus())) {
                point.setFailedCount(point.getFailedCount() + row.getTxnCount());
            }
        }

        return TrendSeries.<PaymentAnalyticsPoint>builder()
                .granularity(granularity).from(dateFrom).to(dateTo).points(List.copyOf(byPeriod.values())).build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasPayableProgress(UUID orderId) {
        return paymentRepository.existsByOrderIdAndPaymentStatusInAndDeletedFalse(
                orderId, List.of(PaymentStatus.SUCCESS, PaymentStatus.PENDING));
    }

    /** Prevent payment for cancelled orders: order-service is the source of truth for order
     *  status, so this is a synchronous call rather than a locally-cached copy - payment
     *  initiation is infrequent enough (once per order, occasionally retried) that the extra
     *  round trip is a fair trade for never acting on stale order state. */
    private OrderStatusResponse verifyOrderIsPayable(UUID orderId) {
        OrderStatusResponse order;
        try {
            order = orderServiceClient.getOrder(orderId).block();
        } catch (WebClientResponseException.NotFound ex) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        } catch (WebClientException ex) {
            throw new PaymentException("Could not verify order status right now. Please try again.");
        } catch (RuntimeException ex) {
            // Covers the 5s .timeout() on OrderServiceClient.getOrder() - Mono.block() wraps its
            // checked TimeoutException in a RuntimeException, so it doesn't match WebClientException.
            if (ex.getCause() instanceof java.util.concurrent.TimeoutException) {
                throw new PaymentException("Could not verify order status right now. Please try again.");
            }
            throw ex;
        }
        if (order == null) {
            throw new ResourceNotFoundException("Order not found: " + orderId);
        }
        if ("CANCELLED".equals(order.getStatus())) {
            throw new PaymentException("Cannot pay for a cancelled order: " + orderId);
        }
        return order;
    }

    /** Publishes a Spring application event from inside the current transaction; the actual
     *  Kafka send happens off-thread, only after commit (see PaymentEventListener) - this is
     *  the "asynchronous event publishing after successful payment" mechanism used by every
     *  status-changing path in this class. */
    private void publishStatusChanged(Payment payment, String eventType) {
        eventPublisher.publishEvent(new PaymentStatusChangedEvent(this, payment, eventType));
    }

    private Payment resolvePayment(UUID id, UUID customerId, boolean isAdmin) {
        if (isAdmin) {
            return paymentRepository.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
        }
        return paymentRepository.findByIdAndCustomerIdAndDeletedFalse(id, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }
}
