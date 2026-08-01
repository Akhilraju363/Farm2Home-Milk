package com.farm2home.common.export;

import java.util.function.Function;

/**
 * One column of a tabular export: its header text and how to render a row's cell value as text.
 */
public record ExportColumn<T>(String header, Function<T, String> valueExtractor) {
}
