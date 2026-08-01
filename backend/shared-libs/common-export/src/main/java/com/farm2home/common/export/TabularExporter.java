package com.farm2home.common.export;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

/**
 * Streams a batched dataset out as a single tabular file (CSV/Excel/PDF). Implementations
 * pull rows from the {@link BatchSupplier} incrementally rather than requiring the caller to
 * materialize the entire dataset up front, so callers should back the supplier with a
 * paginated repository query (reusing the same {@code Specification} used for search/reports)
 * to keep memory bounded regardless of dataset size.
 */
public interface TabularExporter<T> {

    int DEFAULT_BATCH_SIZE = 500;

    void write(OutputStream out, List<String> headers, List<ExportColumn<T>> columns, BatchSupplier<T> supplier)
            throws IOException;
}
