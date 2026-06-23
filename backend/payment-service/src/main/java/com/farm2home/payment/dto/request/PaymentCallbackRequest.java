package com.farm2home.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class PaymentCallbackRequest {

    @NotBlank(message = "Payment reference is required")
    private String paymentReference;

    @NotNull(message = "Success flag is required")
    private Boolean success;

    private String gatewayResponse;
    private String errorMessage;
}
