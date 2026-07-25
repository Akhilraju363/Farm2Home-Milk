package com.farm2home.payment.service.impl;

import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.enums.PaymentMethod;
import com.farm2home.payment.domain.enums.PaymentStatus;
import com.farm2home.payment.domain.repository.PaymentRepository;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.payment.exception.PaymentException;
import com.farm2home.payment.exception.ResourceNotFoundException;
import com.farm2home.payment.kafka.PaymentEventProducer;
import com.farm2home.payment.mapper.PaymentMapper;
import com.farm2home.payment.service.PaymentService;
import com.farm2home.payment.service.WalletService;
import com.farm2home.common.core.audit.AuditAction;
import com.farm2home.common.core.audit.AuditLogService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final WalletService walletService;
    private final PaymentMapper mapper;
    private final PaymentEventProducer eventProducer;
    private final AuditLogService auditLogService;

    @Override
    @Transactional
    public PaymentResponse initiate(InitiatePaymentRequest request, UUID customerId) {
        UUID resolvedCustomerId = request.getCustomerId() != null ? request.getCustomerId() : customerId;

        // Reject if a successful payment already exists for this order
        if (paymentRepository.existsByOrderIdAndPaymentStatusAndDeletedFalse(
                request.getOrderId(), PaymentStatus.SUCCESS)) {
            throw new PaymentException("Order " + request.getOrderId() + " has already been paid");
        }

        String reference = "PAY-" + System.currentTimeMillis() + "-" +
                UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Payment payment = Payment.builder()
                .orderId(request.getOrderId())
                .customerId(resolvedCustomerId)
                .paymentReference(reference)
                .amount(request.getAmount())
                .paymentMethod(request.getPaymentMethod())
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        if (request.getPaymentMethod() == PaymentMethod.WALLET) {
            // Debit wallet immediately — if insufficient balance, exception is thrown before saving
            walletService.debitForPayment(resolvedCustomerId, request.getAmount(), null);
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            Payment saved = paymentRepository.save(payment);
            // Update wallet transaction with the actual payment ID
            log.debug("Wallet payment processed for order {}", request.getOrderId());
            eventProducer.publishPaymentEvent(saved, "PAYMENT_SUCCESS");
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

        // UPI / RAZORPAY — stays PENDING, gateway will call back
        Payment saved = paymentRepository.save(payment);
        log.debug("Gateway payment {} initiated for order {}", reference, request.getOrderId());
        auditLogService.record(AuditAction.PAYMENT, "Payment", saved.getId().toString(),
                resolvedCustomerId.toString(),
                "Gateway payment " + reference + " initiated for order " + request.getOrderId());
        return mapper.toResponse(saved);
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

        if (Boolean.TRUE.equals(request.getSuccess())) {
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setPaidAt(LocalDateTime.now());
            Payment saved = paymentRepository.save(payment);
            eventProducer.publishPaymentEvent(saved, "PAYMENT_SUCCESS");
            auditLogService.record(AuditAction.PAYMENT, "Payment", saved.getId().toString(),
                    saved.getCustomerId().toString(),
                    "Gateway callback: payment " + request.getPaymentReference() + " succeeded");
            return mapper.toResponse(saved);
        } else {
            payment.setPaymentStatus(PaymentStatus.FAILED);
            Payment saved = paymentRepository.save(payment);
            eventProducer.publishPaymentEvent(saved, "PAYMENT_FAILED");
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

        payment.setPaymentStatus(PaymentStatus.REFUNDED);
        Payment saved = paymentRepository.save(payment);

        // For WALLET and online payments, credit the refund back to wallet
        if (payment.getPaymentMethod() == PaymentMethod.WALLET ||
            payment.getPaymentMethod() == PaymentMethod.UPI ||
            payment.getPaymentMethod() == PaymentMethod.RAZORPAY) {
            walletService.creditRefund(payment.getCustomerId(), payment.getAmount(), saved.getId());
        }

        eventProducer.publishPaymentEvent(saved, "PAYMENT_REFUNDED");
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

    private Payment resolvePayment(UUID id, UUID customerId, boolean isAdmin) {
        if (isAdmin) {
            return paymentRepository.findByIdAndDeletedFalse(id)
                    .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
        }
        return paymentRepository.findByIdAndCustomerIdAndDeletedFalse(id, customerId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found: " + id));
    }
}
