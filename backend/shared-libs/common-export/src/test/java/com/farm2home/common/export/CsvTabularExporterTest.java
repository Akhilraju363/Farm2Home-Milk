package com.farm2home.common.export;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvTabularExporterTest {

    private final CsvTabularExporter<String> exporter = new CsvTabularExporter<>();
    private final List<ExportColumn<String>> columns = List.of(
            new ExportColumn<>("Value", s -> s),
            new ExportColumn<>("Length", s -> String.valueOf(s.length())));

    @Test
    void writesHeaderAndAllBatchedRows() throws IOException {
        List<List<String>> batches = List.of(List.of("alpha", "beta"), List.of("gamma"), List.of());
        BatchSupplier<String> supplier = (page, size) -> page < batches.size() ? batches.get(page) : List.of();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of("Value", "Length"), columns, supplier);

        List<CSVRecord> records;
        try (CSVParser parser = CSVParser.parse(
                new InputStreamReader(new ByteArrayInputStream(out.toByteArray()), StandardCharsets.UTF_8),
                CSVFormat.DEFAULT.builder().setHeader().build())) {
            records = parser.getRecords();
        }

        assertThat(records).hasSize(3);
        assertThat(records.get(0).get("Value")).isEqualTo("alpha");
        assertThat(records.get(0).get("Length")).isEqualTo("5");
        assertThat(records.get(2).get("Value")).isEqualTo("gamma");
    }

    @Test
    void writesOnlyHeaderWhenSupplierIsEmpty() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of("Value", "Length"), columns, (page, size) -> List.of());

        String content = out.toString(StandardCharsets.UTF_8);
        assertThat(content.trim()).isEqualTo("Value,Length");
    }
}
