package com.farm2home.payment.dto.request;

import com.farm2home.common.core.constants.ValidationConstants;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaymentCallbackRequest {

    @NotBlank(message = "Payment reference is required")
    @Schema(description = "The payment's own `paymentReference` (as returned by initiate), not the payment's "
            + "UUID id. Must belong to a payment currently in PENDING state.", example = "PAY-1753900000000-A1B2C3D4")
    private String paymentReference;

    @NotNull(message = "Success flag is required")
    @Schema(description = "true transitions the payment to SUCCESS; false transitions it to FAILED.", example = "true")
    private Boolean success;

    @Size(max = ValidationConstants.NOTES_MAX_LENGTH, message = "Gateway response must be at most 255 characters")
    @Schema(description = "Optional free-text gateway response payload/notes to store on the payment. "
            + "Must be at most 255 characters.", example = "Simulated success via manual callback")
    private String gatewayResponse;
    @Size(max = ValidationConstants.NOTES_MAX_LENGTH, message = "Error message must be at most 255 characters")
    @Schema(description = "Optional error message, typically set when success=false. Must be at most 255 "
            + "characters. Not currently persisted on the payment (see PaymentServiceImpl.processCallback — "
            + "only gatewayResponse and gatewayPaymentId are stored).", example = "Card declined by issuing bank")
    private String errorMessage;

    @Schema(description = "Gateway payment ID, if known — lets a manually-triggered callback still "
            + "link the payment for a later gateway-backed refund, same as the real verify()/webhook paths")
    private String gatewayPaymentId;
}
