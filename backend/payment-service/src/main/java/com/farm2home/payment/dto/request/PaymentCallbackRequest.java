package com.farm2home.payment.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentCallbackRequest {

    @NotBlank(message = "Payment reference is required")
    private String paymentReference;

    @NotNull(message = "Success flag is required")
    private Boolean success;

    @Size(max = 255, message = "Gateway response must be at most 255 characters")
    private String gatewayResponse;
    @Size(max = 255, message = "Error message must be at most 255 characters")
    private String errorMessage;
}
