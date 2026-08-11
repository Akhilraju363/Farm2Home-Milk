package com.farm2home.invoice.controller;

import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.invoice.config.UserPrincipal;
import com.farm2home.invoice.dto.response.InvoiceResponse;
import com.farm2home.invoice.dto.response.InvoiceSummaryResponse;
import com.farm2home.invoice.service.impl.InvoiceServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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

import java.time.LocalDate;
import java.util.UUID;

/** Invoices are generated on demand by an admin from an existing, already-placed order - there is
 *  no automatic generation trigger anywhere in the backend (no Kafka event, no payment-success
 *  hook), and none is added here; that would be inventing a business rule this platform has never
 *  specified. See InvoiceServiceImpl for the read-through composition from order/payment/customer
 *  data, and InvoicePdfRenderer for the real backend-generated PDF (not a client-side one). */
@RestController
@RequestMapping("/api/v1/invoices")
@Tag(name = "Invoices", description = "Admin-generated billing documents for orders")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class InvoiceController {

    private final InvoiceServiceImpl invoiceService;

    @PostMapping("/generate/{orderId}")
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Generate an invoice for an order",
            description = "Snapshots the order's current totalAmount as the invoice's subtotal/total (no "
                    + "tax or delivery charge - neither exists anywhere in the backend). One invoice per "
                    + "order - a second attempt on the same order returns 409. Does not require the order "
                    + "to be paid or delivered first; payment info is simply absent from the response until "
                    + "a payment exists.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Invoice generated"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No order exists with the given id", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409",
                description = "An invoice already exists for this order", content = @Content)
    })
    public ResponseEntity<ApiResponse<InvoiceResponse>> generate(@PathVariable UUID orderId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Invoice generated successfully", invoiceService.generate(orderId)));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "List/search all invoices",
            description = "FARM_MANAGER/SUPER_ADMIN only. Keyword matches the invoice number; all filters "
                    + "are optional and combine with AND. There is no status filter - invoices have no "
                    + "independent status of their own (see the Phase 1 audit).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoices retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Caller lacks FARM_MANAGER/SUPER_ADMIN", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<InvoiceSummaryResponse>>> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID customerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @PageableDefault(size = 20, sort = "issueDate") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Invoices retrieved successfully",
                invoiceService.search(keyword, customerId, dateFrom, dateTo, pageable)));
    }

    @GetMapping("/me")
    @Operation(summary = "List the caller's own invoices",
            description = "Self-service - any authenticated caller sees only invoices for their own "
                    + "customer id (resolved from the bearer token).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoices retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<InvoiceSummaryResponse>>> findMine(
            @AuthenticationPrincipal UserPrincipal principal,
            @PageableDefault(size = 20, sort = "issueDate") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Invoices retrieved successfully",
                invoiceService.findMine(principal.userId(), pageable)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get invoice by id",
            description = "Ownership is enforced server-side: FARM_MANAGER/SUPER_ADMIN can fetch any "
                    + "invoice, everyone else only their own. 404 (not 403) if the id doesn't exist or "
                    + "belongs to someone else, so a mismatched id never confirms whether it exists.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoice retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No such invoice for this caller", content = @Content)
    })
    public ResponseEntity<ApiResponse<InvoiceResponse>> findById(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Invoice retrieved successfully", invoiceService.findById(id, principal)));
    }

    @GetMapping("/order/{orderId}")
    @Operation(summary = "Get the invoice generated for an order, if one exists",
            description = "Used by the Order Details page to decide whether to show \"View Invoice\" or "
                    + "\"Generate Invoice\". Same ownership scoping as GET /{id}.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Invoice retrieved"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No invoice exists for this order (yet), or it belongs to someone else", content = @Content)
    })
    public ResponseEntity<ApiResponse<InvoiceResponse>> findByOrderId(
            @PathVariable UUID orderId, @AuthenticationPrincipal UserPrincipal principal) {
        return ResponseEntity.ok(ApiResponse.success("Invoice retrieved successfully", invoiceService.findByOrderId(orderId, principal)));
    }

    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @Operation(summary = "Download the invoice as a PDF",
            description = "Generated on the backend at request time from the same data GET /{id} returns "
                    + "(not a client-side/browser-generated PDF). Same ownership scoping as GET /{id}.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "PDF file stream"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401", description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "No such invoice for this caller", content = @Content)
    })
    public ResponseEntity<byte[]> downloadPdf(
            @PathVariable UUID id, @AuthenticationPrincipal UserPrincipal principal) {
        byte[] pdf = invoiceService.generatePdf(id, principal);
        String filename = "invoice-" + id + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
