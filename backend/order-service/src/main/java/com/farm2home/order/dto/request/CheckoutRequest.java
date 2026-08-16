package com.farm2home.order.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/** Deliberately carries no items/prices - checkout always sources them from the caller's own
 *  server-side cart (see OrderServiceImpl.checkout()), so a client can never influence which
 *  products/quantities/prices actually get ordered. */
@Data
public class CheckoutRequest {

    @NotNull(message = "Order date is required")
    @Schema(example = "2026-08-20", requiredMode = Schema.RequiredMode.REQUIRED)
    private LocalDate orderDate;
}
