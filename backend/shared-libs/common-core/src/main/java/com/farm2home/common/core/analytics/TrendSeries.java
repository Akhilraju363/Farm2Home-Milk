package com.farm2home.common.core.analytics;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.List;

/** Generic chart-ready response envelope for every analytics trend endpoint: the requested
 *  granularity/date range plus an ordered list of period data points. Deliberately holds no
 *  chart-library-specific shape (no colors, labels, dataset wrappers) - just plain data. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrendSeries<T> {

    @Schema(example = "DAILY")
    private Granularity granularity;

    @Schema(description = "Requested range start (inclusive); null if unbounded", example = "2026-06-01")
    private LocalDate from;

    @Schema(description = "Requested range end (inclusive); null if unbounded", example = "2026-06-30")
    private LocalDate to;

    @Schema(description = "One entry per period in the range, in chronological order")
    private List<T> points;
}
