package com.farm2home.common.export;

import java.util.List;

/**
 * Supplies export rows one bounded batch at a time (e.g. backed by a paginated repository
 * query) so exporters never need to hold a full large dataset in memory at once. An empty
 * list signals there is no more data.
 */
@FunctionalInterface
public interface BatchSupplier<T> {

    List<T> nextBatch(int pageNumber, int batchSize);
}
