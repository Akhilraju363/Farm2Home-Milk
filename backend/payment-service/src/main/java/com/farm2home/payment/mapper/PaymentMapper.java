package com.farm2home.payment.mapper;

import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.entity.Wallet;
import com.farm2home.payment.domain.entity.WalletTransaction;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.payment.dto.response.WalletResponse;
import com.farm2home.payment.dto.response.WalletTransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "paymentMethod", expression = "java(payment.getPaymentMethod().name())")
    @Mapping(target = "paymentStatus", expression = "java(payment.getPaymentStatus().name())")
    // Only ever set right after initiate() creates a gateway order — the service layer fills it
    // in manually on that one response, never derived from the persisted entity.
    @Mapping(target = "gatewayCheckoutKeyId", ignore = true)
    PaymentResponse toResponse(Payment payment);

    WalletResponse toWalletResponse(Wallet wallet);

    @Mapping(target = "transactionType", expression = "java(tx.getTransactionType().name())")
    WalletTransactionResponse toTransactionResponse(WalletTransaction tx);

    // customerId needs request-vs-principal resolution, paymentReference is generated, and
    // paymentStatus starts PENDING before the method-specific branch in the service decides
    // its outcome — all assigned by the service after this runs.
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "customerId", ignore = true)
    @Mapping(target = "paymentReference", ignore = true)
    @Mapping(target = "paymentStatus", ignore = true)
    @Mapping(target = "gatewayResponse", ignore = true)
    @Mapping(target = "gatewayOrderId", ignore = true)
    @Mapping(target = "gatewayPaymentId", ignore = true)
    @Mapping(target = "gatewayRefundId", ignore = true)
    @Mapping(target = "paidAt", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    Payment toEntity(InitiatePaymentRequest request);
}
