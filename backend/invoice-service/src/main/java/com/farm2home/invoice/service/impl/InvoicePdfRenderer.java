package com.farm2home.invoice.service.impl;

import com.farm2home.invoice.dto.response.InvoiceResponse;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a single invoice as a structured PDF document - a real per-record layout (header,
 * customer block, line-item table, totals, payment block), not the shared shared-libs/common-export
 * TabularExporter (which only draws a multi-row grid dump, no computed totals or document
 * sections - see the Phase 1 backend audit). Deliberately no logo/letterhead image: the "Farm /
 * Business Information" header uses the same static "Farm2Home Milk" platform branding the rest
 * of the app already uses (TopBar, Sidebar, login page) - Order has no farmId field, and Farm is
 * a registry of N farms with no default/primary designation, so there is no real relationship to
 * pick a specific registered farm's details from; inventing one would violate "do not invent
 * fields not backed by data."
 */
@Component
public class InvoicePdfRenderer {

    private static final float MARGIN = 50f;
    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd MMM yyyy");

    public byte[] render(InvoiceResponse invoice) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDFont bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {
                float width = PDRectangle.A4.getWidth();
                float y = PDRectangle.A4.getHeight() - MARGIN;

                y = drawHeader(cs, bold, regular, width, y);
                y -= 20;
                y = drawPartyBlocks(cs, bold, regular, width, y, invoice);
                y -= 25;
                y = drawInvoiceMeta(cs, bold, regular, width, y, invoice);
                y -= 25;
                y = drawLineItemsTable(cs, bold, regular, width, y, invoice.getItems());
                y -= 15;
                y = drawTotals(cs, bold, regular, width, y, invoice);
                if (invoice.getPayment() != null) {
                    y -= 30;
                    drawPaymentBlock(cs, bold, regular, y, invoice.getPayment());
                }
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to render invoice PDF", e);
        }
    }

    private float drawHeader(PDPageContentStream cs, PDFont bold, PDFont regular, float width, float y) throws IOException {
        text(cs, bold, 20, MARGIN, y, "Farm2Home Milk");
        y -= 16;
        text(cs, regular, 9, MARGIN, y, "support@farm2homemilk.example");
        y -= 20;
        cs.setLineWidth(1f);
        cs.moveTo(MARGIN, y);
        cs.lineTo(width - MARGIN, y);
        cs.stroke();
        return y;
    }

    private float drawPartyBlocks(PDPageContentStream cs, PDFont bold, PDFont regular, float width, float y, InvoiceResponse invoice) throws IOException {
        float startY = y;
        text(cs, bold, 10, MARGIN, y, "Bill To");
        float ly = y - 14;
        if (invoice.getCustomerName() != null) {
            text(cs, regular, 10, MARGIN, ly, invoice.getCustomerName());
            ly -= 13;
        }
        InvoiceResponse.Address addr = invoice.getBillingAddress();
        if (addr != null) {
            if (addr.getAddressLine1() != null) { text(cs, regular, 9, MARGIN, ly, addr.getAddressLine1()); ly -= 12; }
            if (addr.getAddressLine2() != null) { text(cs, regular, 9, MARGIN, ly, addr.getAddressLine2()); ly -= 12; }
            String cityLine = String.join(", ", nonNull(addr.getCity()), nonNull(addr.getState()), nonNull(addr.getPincode())).replaceAll("^, |, $|, (?=,)", "");
            if (!cityLine.isBlank()) { text(cs, regular, 9, MARGIN, ly, cityLine); ly -= 12; }
        }
        if (invoice.getCustomerMobile() != null) { text(cs, regular, 9, MARGIN, ly, "Mobile: " + invoice.getCustomerMobile()); ly -= 12; }
        if (invoice.getCustomerEmail() != null) { text(cs, regular, 9, MARGIN, ly, invoice.getCustomerEmail()); ly -= 12; }
        return Math.min(ly, startY - 14);
    }

    private float drawInvoiceMeta(PDPageContentStream cs, PDFont bold, PDFont regular, float width, float y, InvoiceResponse invoice) throws IOException {
        float labelX = MARGIN, valueX = MARGIN + 110;
        text(cs, bold, 10, labelX, y, "Invoice Number:");
        text(cs, regular, 10, valueX, y, invoice.getInvoiceNumber());
        y -= 14;
        text(cs, bold, 10, labelX, y, "Order Number:");
        text(cs, regular, 10, valueX, y, invoice.getOrderNumber() != null ? invoice.getOrderNumber() : "-");
        y -= 14;
        text(cs, bold, 10, labelX, y, "Issue Date:");
        text(cs, regular, 10, valueX, y, invoice.getIssueDate() != null ? invoice.getIssueDate().format(DATE_FMT) : "-");
        y -= 14;
        text(cs, bold, 10, labelX, y, "Order Status:");
        text(cs, regular, 10, valueX, y, invoice.getOrderStatus() != null ? invoice.getOrderStatus() : "-");
        return y;
    }

    private float drawLineItemsTable(PDPageContentStream cs, PDFont bold, PDFont regular, float width, float y, List<InvoiceResponse.LineItem> items) throws IOException {
        float[] colX = {MARGIN, MARGIN + 220, MARGIN + 320, MARGIN + 420};
        float tableRight = width - MARGIN;

        cs.setNonStrokingColor(0.93f, 0.93f, 0.93f);
        cs.addRect(MARGIN, y - 4, tableRight - MARGIN, 18);
        cs.fill();
        cs.setNonStrokingColor(0f, 0f, 0f);

        text(cs, bold, 9, colX[0] + 4, y, "Milk Type");
        text(cs, bold, 9, colX[1], y, "Quantity (L)");
        text(cs, bold, 9, colX[2], y, "Unit Price");
        text(cs, bold, 9, colX[3], y, "Subtotal");
        y -= 20;

        if (items == null || items.isEmpty()) {
            text(cs, regular, 9, colX[0] + 4, y, "No line items available.");
            y -= 16;
        } else {
            for (InvoiceResponse.LineItem item : items) {
                text(cs, regular, 9, colX[0] + 4, y, item.getMilkType() != null ? item.getMilkType() : "-");
                text(cs, regular, 9, colX[1], y, money(item.getQuantity()));
                text(cs, regular, 9, colX[2], y, "Rs " + money(item.getUnitPrice()));
                text(cs, regular, 9, colX[3], y, "Rs " + money(item.getTotalPrice()));
                y -= 16;
            }
        }
        cs.setLineWidth(0.5f);
        cs.moveTo(MARGIN, y + 8);
        cs.lineTo(tableRight, y + 8);
        cs.stroke();
        return y;
    }

    private float drawTotals(PDPageContentStream cs, PDFont bold, PDFont regular, float width, float y, InvoiceResponse invoice) throws IOException {
        float labelX = width - MARGIN - 160, valueX = width - MARGIN - 60;
        text(cs, regular, 10, labelX, y, "Subtotal:");
        text(cs, regular, 10, valueX, y, "Rs " + money(invoice.getSubtotal()));
        y -= 16;
        text(cs, bold, 11, labelX, y, "Total Amount:");
        text(cs, bold, 11, valueX, y, "Rs " + money(invoice.getTotalAmount()));
        return y;
    }

    private void drawPaymentBlock(PDPageContentStream cs, PDFont bold, PDFont regular, float y, InvoiceResponse.PaymentInfo payment) throws IOException {
        text(cs, bold, 10, MARGIN, y, "Payment Information");
        y -= 14;
        text(cs, regular, 9, MARGIN, y, "Method: " + nonNull(payment.getPaymentMethod()));
        y -= 12;
        text(cs, regular, 9, MARGIN, y, "Reference: " + nonNull(payment.getPaymentReference()));
        y -= 12;
        text(cs, regular, 9, MARGIN, y, "Status: " + nonNull(payment.getPaymentStatus()));
        if (payment.getPaidAt() != null) {
            y -= 12;
            text(cs, regular, 9, MARGIN, y, "Paid At: " + payment.getPaidAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy, hh:mm a")));
        }
    }

    private void text(PDPageContentStream cs, PDFont font, float size, float x, float y, String value) throws IOException {
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(value == null ? "" : value);
        cs.endText();
    }

    private String money(BigDecimal value) {
        return value == null ? "0.00" : value.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private String nonNull(String value) {
        return value == null ? "" : value;
    }
}
