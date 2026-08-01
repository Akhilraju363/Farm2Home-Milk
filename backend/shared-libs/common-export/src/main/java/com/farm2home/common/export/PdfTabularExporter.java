package com.farm2home.common.export;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Builds a simple tabular PDF via PDFBox, paginating rows across pages and repeating the
 * header row on each new page. Unlike CSV/Excel, a PDF's cross-reference table means the
 * complete document object graph must be known before any bytes can be written, so this
 * cannot stream bytes to the HTTP response incrementally the way the other two formats do -
 * rows are appended to the in-memory/temp-backed PDDocument as each batch is fetched (so the
 * full dataset is never materialized as a single Java collection), and the finished file is
 * written to the output stream once at the end.
 */
public class PdfTabularExporter<T> implements TabularExporter<T> {

    private static final float MARGIN = 30f;
    private static final float ROW_HEIGHT = 16f;
    private static final float FONT_SIZE = 8f;
    private static final PDRectangle PAGE_SIZE = new PDRectangle(PDRectangle.A4.getHeight(), PDRectangle.A4.getWidth());

    @Override
    public void write(OutputStream out, List<String> headers, List<ExportColumn<T>> columns, BatchSupplier<T> supplier)
            throws IOException {
        PDFont headerFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
        PDFont bodyFont = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
        float columnWidth = (PAGE_SIZE.getWidth() - 2 * MARGIN) / Math.max(1, headers.size());

        try (PDDocument document = new PDDocument()) {
            State state = new State(document);
            startPage(state, headers, columnWidth, headerFont);

            int pageNumber = 0;
            List<T> batch;
            while (!(batch = supplier.nextBatch(pageNumber, DEFAULT_BATCH_SIZE)).isEmpty()) {
                for (T item : batch) {
                    if (state.y < MARGIN + ROW_HEIGHT) {
                        state.contentStream.close();
                        startPage(state, headers, columnWidth, headerFont);
                    }
                    drawRow(state, columns.stream().map(c -> c.valueExtractor().apply(item)).toList(), columnWidth, bodyFont);
                }
                pageNumber++;
            }
            state.contentStream.close();
            document.save(out);
        }
    }

    private void startPage(State state, List<String> headers, float columnWidth, PDFont headerFont) throws IOException {
        PDPage page = new PDPage(PAGE_SIZE);
        state.document.addPage(page);
        state.contentStream = new PDPageContentStream(state.document, page);
        state.y = PAGE_SIZE.getHeight() - MARGIN;
        drawRow(state, headers, columnWidth, headerFont);
    }

    private void drawRow(State state, List<String> values, float columnWidth, PDFont font) throws IOException {
        PDPageContentStream cs = state.contentStream;
        float x = MARGIN;
        for (String value : values) {
            String text = truncate(value == null ? "" : value, columnWidth, font);
            cs.beginText();
            cs.setFont(font, FONT_SIZE);
            cs.newLineAtOffset(x, state.y);
            cs.showText(text);
            cs.endText();
            x += columnWidth;
        }
        state.y -= ROW_HEIGHT;
    }

    private String truncate(String value, float columnWidth, PDFont font) throws IOException {
        String text = value;
        float maxWidth = columnWidth - 4f;
        while (!text.isEmpty() && font.getStringWidth(text) / 1000 * FONT_SIZE > maxWidth) {
            text = text.substring(0, text.length() - 1);
        }
        return text;
    }

    private static final class State {
        final PDDocument document;
        PDPageContentStream contentStream;
        float y;

        State(PDDocument document) {
            this.document = document;
        }
    }
}
