package com.farm2home.common.core.analytics;

/** Time-bucket size for a trend endpoint. {@link #getSqlUnit()} is the literal PostgreSQL
 *  {@code date_trunc(unit, ...)} field name, bound as a query parameter (not string-concatenated)
 *  by every analytics repository query. */
public enum Granularity {

    DAILY("day"),
    WEEKLY("week"),
    MONTHLY("month"),
    YEARLY("year");

    private final String sqlUnit;

    Granularity(String sqlUnit) {
        this.sqlUnit = sqlUnit;
    }

    public String getSqlUnit() {
        return sqlUnit;
    }
}
