package com.farm2home.common.core.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Shared response envelope for every report endpoint: a page of rows (mirroring Spring Data's
 * Page shape: content/pageNumber/pageSize/totalElements/totalPages) plus report-specific summary
 * totals computed via the same filter criteria as the page itself. Defined once here so each
 * owning service (producer) and reports-service (consumer) deserialize the identical shape.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportPage<T, S> {
    @Schema(description = "Rows for the current page, matching the request's filters")
    private List<T> content;
    @Schema(description = "Zero-based page index", example = "0")
    private int pageNumber;
    @Schema(example = "20")
    private int pageSize;
    @Schema(description = "Total rows matching the filters, across all pages", example = "128")
    private long totalElements;
    @Schema(example = "7")
    private int totalPages;
    @Schema(description = "Aggregate totals computed over every row matching the filters, "
            + "not just the current page")
    private S summary;
}
