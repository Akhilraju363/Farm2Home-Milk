package com.farm2home.common.export;

import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Uses POI's streaming SXSSF workbook (a fixed window of rows held in memory, older rows
 * flushed to a temp file) instead of XSSFWorkbook so large exports don't require holding the
 * entire spreadsheet in memory.
 */
public class ExcelTabularExporter<T> implements TabularExporter<T> {

    private static final int ROW_ACCESS_WINDOW_SIZE = 100;

    @Override
    public void write(OutputStream out, List<String> headers, List<ExportColumn<T>> columns, BatchSupplier<T> supplier)
            throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(ROW_ACCESS_WINDOW_SIZE)) {
            workbook.setCompressTempFiles(true);
            SXSSFSheet sheet = workbook.createSheet("Export");

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowNum = 1;
            int pageNumber = 0;
            List<T> batch;
            while (!(batch = supplier.nextBatch(pageNumber, DEFAULT_BATCH_SIZE)).isEmpty()) {
                for (T item : batch) {
                    Row row = sheet.createRow(rowNum++);
                    for (int i = 0; i < columns.size(); i++) {
                        row.createCell(i).setCellValue(columns.get(i).valueExtractor().apply(item));
                    }
                }
                pageNumber++;
            }

            workbook.write(out);
            workbook.dispose();
        }
    }
}
