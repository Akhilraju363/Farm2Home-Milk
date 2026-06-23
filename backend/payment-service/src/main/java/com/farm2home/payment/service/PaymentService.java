package com.farm2home.payment.service;

import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.response.PaymentResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    PaymentResponse initiate(InitiatePaymentRequest request, UUID customerId);

    PaymentResponse processCallback(PaymentCallbackRequest request);

    PaymentResponse refund(UUID paymentId, UUID customerId, boolean isAdmin);

    PaymentResponse findById(UUID id, UUID customerId, boolean isAdmin);

    List<PaymentResponse> findByOrderId(UUID orderId, UUID customerId, boolean isAdmin);

    Page<PaymentResponse> findAll(UUID customerId, boolean isAdmin, Pageable pageable);
}
