package com.farm2home.common.export;

public final class TabularExporterFactory {

    private TabularExporterFactory() {
    }

    public static <T> TabularExporter<T> forFormat(ExportFormat format) {
        return switch (format) {
            case CSV -> new CsvTabularExporter<>();
            case EXCEL -> new ExcelTabularExporter<>();
            case PDF -> new PdfTabularExporter<>();
        };
    }
}
