package com.farm2home.payment.mapper;

import com.farm2home.payment.domain.entity.Payment;
import com.farm2home.payment.domain.entity.Wallet;
import com.farm2home.payment.domain.entity.WalletTransaction;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.payment.dto.response.WalletResponse;
import com.farm2home.payment.dto.response.WalletTransactionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {

    @Mapping(target = "paymentMethod", expression = "java(payment.getPaymentMethod().name())")
    @Mapping(target = "paymentStatus", expression = "java(payment.getPaymentStatus().name())")
    PaymentResponse toResponse(Payment payment);

    WalletResponse toWalletResponse(Wallet wallet);

    @Mapping(target = "transactionType", expression = "java(tx.getTransactionType().name())")
    WalletTransactionResponse toTransactionResponse(WalletTransaction tx);
}
