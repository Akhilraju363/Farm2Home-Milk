package com.farm2home.invoice.service;

import com.farm2home.invoice.dto.response.InvoiceResponse;
import com.farm2home.invoice.service.impl.InvoicePdfRenderer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvoicePdfRendererTest {

    private final InvoicePdfRenderer renderer = new InvoicePdfRenderer();

    @Test
    void rendersAValidPdfWithLineItemsAndPayment() {
        InvoiceResponse invoice = InvoiceResponse.builder()
                .invoiceNumber("INV-2026-000001")
                .issueDate(LocalDate.of(2026, 8, 10))
                .orderNumber("ORD-2026-100042")
                .orderDate(LocalDate.of(2026, 8, 9))
                .orderStatus("DELIVERED")
                .customerName("Asha Rao")
                .customerMobile("9876543210")
                .billingAddress(InvoiceResponse.Address.builder()
                        .addressLine1("402, Maple Ave").city("Mumbai").state("Maharashtra").pincode("400001").build())
                .items(List.of(InvoiceResponse.LineItem.builder()
                        .milkType("FULL_CREAM").quantity(new BigDecimal("2.00"))
                        .unitPrice(new BigDecimal("60.00")).totalPrice(new BigDecimal("120.00")).build()))
                .subtotal(new BigDecimal("120.00"))
                .totalAmount(new BigDecimal("120.00"))
                .payment(InvoiceResponse.PaymentInfo.builder()
                        .paymentReference("PAY-123").paymentMethod("UPI").paymentStatus("SUCCESS").build())
                .build();

        byte[] pdf = renderer.render(invoice);

        assertThat(pdf).isNotEmpty();
        // PDF files always start with "%PDF-" - a cheap, real assertion that this is an actual
        // PDF document, not just an arbitrary non-empty byte array.
        assertThat(new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void rendersWithoutPaymentOrItems_noException() {
        InvoiceResponse invoice = InvoiceResponse.builder()
                .invoiceNumber("INV-2026-000002")
                .issueDate(LocalDate.of(2026, 8, 10))
                .subtotal(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .items(List.of())
                .build();

        byte[] pdf = renderer.render(invoice);

        assertThat(pdf).isNotEmpty();
    }
}
