package com.farm2home.invoice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Lighter row shape for the invoice list/search screens - avoids fetching every order's line
 *  items and every customer's address for each row on a paginated list (only InvoiceResponse,
 *  the single-invoice detail view, needs those). Still enriched with orderNumber/customerName/
 *  orderStatus via read-through, same as InvoiceResponse. */
@Data
@Builder
public class InvoiceSummaryResponse {
    private UUID id;
    private String invoiceNumber;
    private LocalDate issueDate;
    private UUID orderId;
    private String orderNumber;
    @Schema(description = "order-service's real order status - there is no separate invoice status", example = "DELIVERED")
    private String orderStatus;
    private UUID customerId;
    private String customerName;
    private BigDecimal totalAmount;
}
