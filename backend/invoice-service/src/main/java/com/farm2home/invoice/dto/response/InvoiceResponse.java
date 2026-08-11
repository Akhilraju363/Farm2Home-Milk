package com.farm2home.invoice.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Composed at read time from Invoice's own snapshot fields plus a live read-through to
 *  order-service/payment-service/customer-service - see InvoiceServiceImpl#compose(). Fields from
 *  a downstream service are null if that service didn't respond in time (see each Client's
 *  .timeout()/.onErrorResume()), never fabricated. */
@Data
@Builder
public class InvoiceResponse {

    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID id;

    @Schema(description = "Backend-generated, unique. Format: INV-{year}-{6-digit sequence}", example = "INV-2026-000042")
    private String invoiceNumber;

    @Schema(example = "2026-08-10")
    private LocalDate issueDate;

    // ── Order ───────────────────────────────────────────────────────────
    @Schema(example = "a1b2c3d4-e5f6-4789-9abc-1234567890ab")
    private UUID orderId;
    @Schema(example = "ORD-2026-100042")
    private String orderNumber;
    @Schema(example = "2026-08-09")
    private LocalDate orderDate;
    @Schema(description = "PENDING, ASSIGNED, OUT_FOR_DELIVERY, DELIVERED, or CANCELLED - order-service's real order status, not an invented invoice status", example = "DELIVERED")
    private String orderStatus;
    private List<LineItem> items;

    // ── Customer ────────────────────────────────────────────────────────
    @Schema(example = "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e")
    private UUID customerId;
    private String customerName;
    private String customerMobile;
    private String customerEmail;
    private Address billingAddress;

    // ── Amounts (no tax/deliveryCharge/discount - none exist in the backend; see the Phase 1
    //    audit. subtotal and totalAmount are always equal until a genuine tax/delivery-charge
    //    model is designed and added) ──────────────────────────────────────
    private BigDecimal subtotal;
    private BigDecimal totalAmount;

    // ── Payment (null if the order has no payment attempt yet - an invoice can be generated
    //    before payment succeeds, since generation is manual/admin-triggered) ─────────────────
    private PaymentInfo payment;

    @Schema(example = "2026-08-10T11:15:00")
    private LocalDateTime createdAt;

    @Data
    @Builder
    public static class LineItem {
        @Schema(example = "FULL_CREAM")
        private String milkType;
        @Schema(description = "Quantity in litres", example = "2.00")
        private BigDecimal quantity;
        @Schema(example = "60.00")
        private BigDecimal unitPrice;
        @Schema(example = "120.00")
        private BigDecimal totalPrice;
    }

    @Data
    @Builder
    public static class Address {
        private String addressLine1;
        private String addressLine2;
        private String city;
        private String state;
        private String pincode;
    }

    @Data
    @Builder
    public static class PaymentInfo {
        private String paymentReference;
        @Schema(description = "UPI, RAZORPAY, WALLET, or CASH", example = "UPI")
        private String paymentMethod;
        @Schema(description = "PENDING, SUCCESS, FAILED, or REFUNDED - payment-service's real payment status", example = "SUCCESS")
        private String paymentStatus;
        private LocalDateTime paidAt;
    }
}
