package com.farm2home.common.export;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class CsvTabularExporter<T> implements TabularExporter<T> {

    @Override
    public void write(OutputStream out, List<String> headers, List<ExportColumn<T>> columns, BatchSupplier<T> supplier)
            throws IOException {
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        CSVFormat format = CSVFormat.DEFAULT.builder().setHeader(headers.toArray(new String[0])).build();
        try (CSVPrinter printer = new CSVPrinter(writer, format)) {
            int pageNumber = 0;
            List<T> batch;
            while (!(batch = supplier.nextBatch(pageNumber, DEFAULT_BATCH_SIZE)).isEmpty()) {
                for (T row : batch) {
                    printer.printRecord(columns.stream().map(c -> c.valueExtractor().apply(row)).toList());
                }
                // Flush after every batch so bytes are actually pushed out over the HTTP
                // response as they're produced instead of buffering the whole file.
                printer.flush();
                pageNumber++;
            }
        }
    }
}
