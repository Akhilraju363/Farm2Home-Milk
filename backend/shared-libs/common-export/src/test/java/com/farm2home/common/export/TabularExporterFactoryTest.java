package com.farm2home.common.export;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

class TabularExporterFactoryTest {

    @ParameterizedTest
    @EnumSource(ExportFormat.class)
    void returnsAMatchingExporterForEveryFormat(ExportFormat format) {
        TabularExporter<Object> exporter = TabularExporterFactory.forFormat(format);

        assertThat(exporter).isInstanceOf(switch (format) {
            case CSV -> CsvTabularExporter.class;
            case EXCEL -> ExcelTabularExporter.class;
            case PDF -> PdfTabularExporter.class;
        });
    }
}
