package com.farm2home.payment.controller;

import com.farm2home.common.core.analytics.Granularity;
import com.farm2home.common.core.analytics.PaymentAnalyticsPoint;
import com.farm2home.common.core.analytics.RevenueTrendPoint;
import com.farm2home.common.core.analytics.TrendSeries;
import com.farm2home.common.core.constants.ApiConstants;
import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.core.dashboard.PaymentSummaryResponse;
import com.farm2home.common.core.reports.PaymentReportRow;
import com.farm2home.common.core.reports.PaymentReportSummary;
import com.farm2home.common.core.reports.ReportPage;
import com.farm2home.common.export.ExportFormat;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.payment.config.UserPrincipal;
import com.farm2home.payment.domain.enums.PaymentStatus;
import com.farm2home.payment.dto.request.InitiatePaymentRequest;
import com.farm2home.payment.dto.request.PaymentCallbackRequest;
import com.farm2home.payment.dto.request.VerifyPaymentRequest;
import com.farm2home.payment.dto.response.PaymentResponse;
import com.farm2home.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/**
 * Note on the {@code @ApiResponse} annotation used below: it is always fully-qualified
 * ({@code io.swagger.v3.oas.annotations.responses.ApiResponse}) rather than imported by simple
 * name, since it collides with this codebase's own {@link ApiResponse} success envelope - the
 * same convention WalletController/OrderController/SubscriptionController already use.
 *
 * Two endpoints ({@code /callback} and {@code /webhook}) are listed in SecurityConfig's
 * PUBLIC_ENDPOINTS and are reachable with no bearer token at all; both override the class-level
 * {@code @SecurityRequirement} with an empty {@code @SecurityRequirements} so the generated
 * OpenAPI spec doesn't claim they need one.
 */
@RestController
@RequestMapping("/api/v1/payments")
@Tag(name = "Payments", description = "Payment processing: initiation, gateway verification/webhook, refunds, "
        + "and admin reporting/analytics.")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping
    @Operation(summary = "Initiate a payment for an order",
            description = "Creates a new payment for an order. Behavior depends on `paymentMethod`: WALLET "
                    + "debits the customer's wallet immediately and the payment is created already in SUCCESS "
                    + "state; CASH (cash-on-delivery) is created PENDING and stays that way until delivery "
                    + "staff mark it paid; UPI/RAZORPAY create a gateway checkout order via the configured "
                    + "PaymentGatewayProvider and stay PENDING until POST /{id}/verify or POST /webhook "
                    + "confirms the outcome. Rejects orders that are CANCELLED (checked synchronously against "
                    + "order-service) or that already have a SUCCESS/PENDING payment recorded for them.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201",
                description = "Payment created",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Payment initiated successfully",
                                  "data": {
                                    "id": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "orderId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "customerId": "1a2b3c4d-5e6f-4a1b-8c9d-0e1f2a3b4c5d",
                                    "paymentReference": "PAY-1753900000000-A1B2C3D4",
                                    "amount": 499.00,
                                    "paymentMethod": "RAZORPAY",
                                    "paymentStatus": "PENDING",
                                    "gatewayOrderId": "order_NxHhkjb2C4gY7a",
                                    "gatewayCheckoutKeyId": "rzp_test_1DP5mmOlF5G5ag",
                                    "createdAt": "2026-07-31T10:15:30"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed; the order is CANCELLED; or the order already has a "
                        + "SUCCESS/PENDING payment", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Order not found in order-service", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                description = "paymentMethod is WALLET and the customer has no wallet yet or an insufficient "
                        + "balance", content = @Content)
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> initiate(
            @Valid @RequestBody InitiatePaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Payment initiated successfully", paymentService.initiate(request, principal.userId())));
    }

    @PostMapping("/callback")
    @SecurityRequirements
    @Operation(summary = "Manual/legacy payment status update",
            description = "Updates a payment by reference without gateway signature verification. Kept for "
                    + "backward compatibility and to simulate gateway outcomes under the mock provider "
                    + "(local dev/tests). Real gateway outcomes should arrive via POST /{id}/verify "
                    + "(client-driven) or POST /webhook (server-to-server) instead. Public — listed in "
                    + "SecurityConfig's PUBLIC_ENDPOINTS alongside /webhook, so no bearer token is required or "
                    + "checked; the payment is resolved purely from `paymentReference`.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Payment status updated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed, or the payment is not currently in PENDING state",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No payment found for the given paymentReference", content = @Content)
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> callback(
            @Valid @RequestBody PaymentCallbackRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Payment status updated successfully", paymentService.processCallback(request)));
    }

    @PostMapping("/{id}/verify")
    @Operation(summary = "Verify a client-reported checkout completion",
            description = "Called by the frontend right after the gateway's checkout widget reports success — "
                    + "verifies the signature (and, where the provider supports it, re-confirms the payment's "
                    + "status directly with the gateway) before transitioning PENDING to SUCCESS or FAILED.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Verification completed — payment transitioned to SUCCESS or FAILED",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Payment verification completed",
                                  "data": {
                                    "id": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "orderId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "customerId": "1a2b3c4d-5e6f-4a1b-8c9d-0e1f2a3b4c5d",
                                    "paymentReference": "PAY-1753900000000-A1B2C3D4",
                                    "amount": 499.00,
                                    "paymentMethod": "RAZORPAY",
                                    "paymentStatus": "SUCCESS",
                                    "gatewayOrderId": "order_NxHhkjb2C4gY7a",
                                    "gatewayPaymentId": "pay_NxHiab12C4gY7b",
                                    "paidAt": "2026-07-31T10:16:05",
                                    "createdAt": "2026-07-31T10:15:30"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Validation failed; the payment is not PENDING; the supplied gatewayOrderId "
                        + "does not match the payment's stored one; or the gateway signature is invalid",
                content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Payment not found, or it belongs to a different customer (a non-admin caller "
                        + "gets the same 404 either way, so the API never confirms another customer's "
                        + "payment exists)", content = @Content)
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> verify(
            @PathVariable UUID id,
            @Valid @RequestBody VerifyPaymentRequest request,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Payment verification completed",
                paymentService.verify(id, request, principal.userId(), principal.isAdmin())));
    }

    @PostMapping("/webhook")
    @SecurityRequirements
    @Operation(summary = "Payment gateway webhook",
            description = "Server-to-server notification from the payment gateway (e.g. Razorpay's "
                    + "payment.captured/payment.failed events). Public — the gateway cannot attach our "
                    + "JWT — but every payload's signature is verified before it's acted on. Idempotent: "
                    + "safe for the gateway to retry the same event; a payment already out of PENDING, or an "
                    + "event for an unrecognized gateway order, is logged and silently ignored rather than "
                    + "rejected.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Event accepted — this is returned once the signature checks out, even if the "
                        + "event turns out to be unmatched/already-processed and is ignored"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "Missing or invalid X-Razorpay-Signature for the given raw payload",
                content = @Content)
    })
    public ResponseEntity<Void> webhook(
            @RequestBody String rawPayload,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        paymentService.handleWebhook(rawPayload, signature);
        return ResponseEntity.ok().build();
    }

    @GetMapping
    @Operation(summary = "List payments (admin sees all, customer sees own)",
            description = "Paginated list of payments, most recent first by default. Admin callers see every "
                    + "payment in the system; non-admin callers only ever see payments where customerId "
                    + "matches their own user ID.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Payments retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<PaymentResponse>>> findAll(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Payments retrieved successfully", paymentService.findAll(
                principal.userId(), principal.isAdmin(), pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get payment by ID",
            description = "Returns a single payment. Non-admin callers can only fetch their own payments — "
                    + "requesting another customer's payment ID returns 404 (not 403), the same response as "
                    + "a truly unknown ID, so the API never confirms whether a payment ID belongs to someone "
                    + "else.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Payment retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Payment not found, or it belongs to a different customer", content = @Content)
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> findById(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Payment retrieved successfully", paymentService.findById(id, principal.userId(), principal.isAdmin())));
    }

    @GetMapping("/order/{orderId}/payment-exists")
    @Operation(summary = "Check whether an order has a SUCCESS or PENDING payment",
            description = "Lightweight existence check for cross-service use (e.g. delivery-service "
                    + "verifying payment progress before dispatching an order). Reveals no payment "
                    + "details, just yes/no, so it's open to any authenticated caller rather than scoped "
                    + "to the order's own customer like the other /payments endpoints.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "true if the order has a SUCCESS or PENDING payment, false otherwise (including "
                        + "when the order has no payments at all)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Boolean>> hasPayableProgress(@PathVariable UUID orderId) {
        return ResponseEntity.ok(ApiResponse.success(
                "Payment existence check completed", paymentService.hasPayableProgress(orderId)));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get all payments for an order",
            description = "Returns every payment attempt recorded for the order (an order can have more than "
                    + "one if an earlier attempt failed and the customer retried). Non-admin callers only see "
                    + "their own payments for the order filtered out of the same list — there is no ownership "
                    + "check on the order itself, so an unknown or empty orderId simply yields an empty list "
                    + "rather than a 404.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Payments retrieved (possibly an empty array if the order has none, or none "
                        + "belonging to the caller)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<PaymentResponse>>> findByOrder(
            @PathVariable UUID orderId,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Order payments retrieved successfully", paymentService.findByOrderId(
                orderId, principal.userId(), principal.isAdmin())));
    }

    @PostMapping("/{id}/refund")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Refund a payment (Farm Manager or Super Admin only)",
            description = "Reverses a SUCCESS payment. WALLET payments are credited back to the customer's "
                    + "in-app wallet; UPI/RAZORPAY payments are refunded through the gateway to their "
                    + "original source (never credited to the wallet); CASH payments are only marked "
                    + "REFUNDED, with any physical cash return handled outside this system. The payment is "
                    + "left untouched if refund processing throws before completion.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Payment refunded",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Payment refunded successfully",
                                  "data": {
                                    "id": "8f14e45f-ceea-467e-adc1-0e1d5a3a1e2b",
                                    "orderId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "customerId": "1a2b3c4d-5e6f-4a1b-8c9d-0e1f2a3b4c5d",
                                    "paymentReference": "PAY-1753900000000-A1B2C3D4",
                                    "amount": 499.00,
                                    "paymentMethod": "RAZORPAY",
                                    "paymentStatus": "REFUNDED",
                                    "gatewayOrderId": "order_NxHhkjb2C4gY7a",
                                    "gatewayPaymentId": "pay_NxHiab12C4gY7b",
                                    "paidAt": "2026-07-31T10:16:05",
                                    "createdAt": "2026-07-31T10:15:30"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400",
                description = "The payment is not currently SUCCESS (e.g. already REFUNDED, or still "
                        + "PENDING/FAILED); or it's a gateway-collected payment with no gatewayPaymentId "
                        + "recorded, so there is nothing to refund through the gateway", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller does not have the FARM_MANAGER or SUPER_ADMIN role", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "Payment not found", content = @Content)
    })
    public ResponseEntity<ApiResponse<PaymentResponse>> refund(
            @PathVariable UUID id,
            @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Payment refunded successfully", paymentService.refund(id, principal.userId(), principal.isAdmin())));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Get payment revenue summary metrics for the dashboard",
            description = "SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER only. Returns today's and this "
                    + "month's SUCCESS payment revenue, computed fresh on every call.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Summary retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller does not have the SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER role",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<PaymentSummaryResponse>> getSummary() {
        return ResponseEntity.ok(ApiResponse.success("Payment summary retrieved successfully", paymentService.getSummary()));
    }

    @GetMapping("/reports")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Payment Report",
            description = "SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER only. Filtered, paginated payments "
                    + "plus summary totals (payment count, total amount, success amount). All filters are "
                    + "optional and combine with AND.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Report retrieved (possibly empty page if no payments match the filters)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller does not have the SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER role",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<ReportPage<PaymentReportRow, PaymentReportSummary>>> getReport(
            @Parameter(description = "Payment creation date range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Payment creation date range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) UUID customerId,
            @PageableDefault(size = ApiConstants.DEFAULT_PAGE_SIZE, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Payment report retrieved successfully",
                paymentService.getReport(dateFrom, dateTo, status, customerId, pageable)));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Export payments", description = "SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER "
            + "only. Streams payments matching the same filters as GET /reports to a downloadable CSV, "
            + "Excel, or PDF file. Runs on Spring MVC's async dispatch thread (via StreamingResponseBody) "
            + "and fetches rows in bounded pages, so large exports don't block a request-handling thread or "
            + "require holding the full result set in memory.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "File stream — Content-Type varies with `format` (CSV/Excel/PDF)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller does not have the SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER role",
                content = @Content)
    })
    public ResponseEntity<StreamingResponseBody> export(
            @RequestParam ExportFormat format,
            @Parameter(description = "Payment creation date range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Payment creation date range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) PaymentStatus status,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "true") boolean ascending) {
        String filename = "payments-" + LocalDate.now().format(DateTimeFormatter.ISO_DATE) + format.getFileExtension();
        StreamingResponseBody body = out ->
                paymentService.export(format, out, dateFrom, dateTo, status, customerId, sortBy, ascending);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(format.getContentType()))
                .body(body);
    }

    @GetMapping("/analytics/revenue-trend")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Revenue Trend", description = "SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER only. "
            + "SUM of SUCCESS payment amounts bucketed by the requested granularity (day/week/month/year). "
            + "dateFrom/dateTo are optional and bound the range of paidAt values considered; the GROUP "
            + "BY/SUM aggregation runs in the database.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Trend series retrieved (possibly empty if no SUCCESS payments fall in range)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller does not have the SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER role",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<TrendSeries<RevenueTrendPoint>>> getRevenueTrend(
            @RequestParam Granularity granularity,
            @Parameter(description = "Range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(ApiResponse.success("Revenue trend retrieved successfully",
                paymentService.getRevenueTrend(granularity, dateFrom, dateTo)));
    }

    @GetMapping("/analytics/payment-trend")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Payment Analytics", description = "SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER "
            + "only. Transaction volume and success/failure mix bucketed by the requested granularity, "
            + "independent of payment status (unlike Revenue Trend, which only counts SUCCESS). "
            + "dateFrom/dateTo bound createdAt; aggregation runs in the database.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Analytics series retrieved (possibly empty if no payments fall in range)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller does not have the SUPER_ADMIN, FARM_MANAGER, or DELIVERY_MANAGER role",
                content = @Content)
    })
    public ResponseEntity<ApiResponse<TrendSeries<PaymentAnalyticsPoint>>> getPaymentAnalytics(
            @RequestParam Granularity granularity,
            @Parameter(description = "Range start (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @Parameter(description = "Range end (inclusive)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo) {
        return ResponseEntity.ok(ApiResponse.success("Payment analytics retrieved successfully",
                paymentService.getPaymentAnalytics(granularity, dateFrom, dateTo)));
    }
}
