package com.farm2home.common.export;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PdfTabularExporterTest {

    private final PdfTabularExporter<String> exporter = new PdfTabularExporter<>();
    private final List<ExportColumn<String>> columns = List.of(new ExportColumn<>("Value", s -> s));

    @Test
    void writesHeaderAndRowsOnASinglePage() throws IOException {
        List<List<String>> batches = List.of(List.of("alpha", "beta"), List.of());
        BatchSupplier<String> supplier = (page, size) -> page < batches.size() ? batches.get(page) : List.of();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of("Value"), columns, supplier);

        try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(document);
            assertThat(text).contains("Value").contains("alpha").contains("beta");
        }
    }

    @Test
    void paginatesAcrossMultiplePagesForLargeDatasets() throws IOException {
        List<String> bigBatch = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            bigBatch.add("row-" + i);
        }
        BatchSupplier<String> supplier = (page, size) -> page == 0 ? bigBatch : List.of();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of("Value"), columns, supplier);

        try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
            assertThat(document.getNumberOfPages()).isGreaterThan(1);
        }
    }

    @Test
    void truncatesValuesWiderThanTheColumn() throws IOException {
        String longValue = "x".repeat(500);
        BatchSupplier<String> supplier = (page, size) -> page == 0 ? List.of(longValue) : List.of();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of("Value"), columns, supplier);

        try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
        }
    }

    @Test
    void writesOnlyHeaderWhenSupplierIsEmpty() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of("Value"), columns, (page, size) -> List.of());

        try (PDDocument document = Loader.loadPDF(out.toByteArray())) {
            assertThat(document.getNumberOfPages()).isEqualTo(1);
            assertThat(new PDFTextStripper().getText(document)).contains("Value");
        }
    }
}
