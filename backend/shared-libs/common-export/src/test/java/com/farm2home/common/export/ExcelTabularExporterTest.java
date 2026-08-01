package com.farm2home.common.export;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelTabularExporterTest {

    private final ExcelTabularExporter<Integer> exporter = new ExcelTabularExporter<>();
    private final List<ExportColumn<Integer>> columns = List.of(
            new ExportColumn<>("Number", String::valueOf),
            new ExportColumn<>("Squared", n -> String.valueOf(n * n)));

    @Test
    void writesHeaderAndAllBatchedRowsAcrossMultipleBatches() throws IOException {
        List<List<Integer>> batches = List.of(List.of(1, 2), List.of(3), List.of());
        BatchSupplier<Integer> supplier = (page, size) -> page < batches.size() ? batches.get(page) : List.of();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of("Number", "Squared"), columns, supplier);

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("Number");
            assertThat(sheet.getRow(0).getCell(1).getStringCellValue()).isEqualTo("Squared");

            List<String> values = new ArrayList<>();
            for (int i = 1; i <= 3; i++) {
                Row row = sheet.getRow(i);
                values.add(row.getCell(0).getStringCellValue() + "/" + row.getCell(1).getStringCellValue());
            }
            assertThat(values).containsExactly("1/1", "2/4", "3/9");
        }
    }

    @Test
    void writesOnlyHeaderWhenSupplierIsEmpty() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        exporter.write(out, List.of("Number", "Squared"), columns, (page, size) -> List.of());

        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(out.toByteArray()))) {
            Sheet sheet = workbook.getSheetAt(0);
            assertThat(sheet.getLastRowNum()).isZero();
            assertThat(sheet.getRow(1)).isNull();
        }
    }
}
