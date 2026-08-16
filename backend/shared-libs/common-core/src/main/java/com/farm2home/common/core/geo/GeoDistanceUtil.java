package com.farm2home.common.core.geo;

import java.math.BigDecimal;

/**
 * Great-circle distance between two lat/lng points via the Haversine formula. Shared so
 * every caller (currently customer-service, for delivery-radius eligibility) uses the exact
 * same calculation - there is no per-service reason for this pure function to vary.
 */
public final class GeoDistanceUtil {

    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistanceUtil() {
    }

    public static double haversineKm(BigDecimal lat1, BigDecimal lon1, BigDecimal lat2, BigDecimal lon2) {
        double lat1Rad = Math.toRadians(lat1.doubleValue());
        double lat2Rad = Math.toRadians(lat2.doubleValue());
        double deltaLatRad = Math.toRadians(lat2.subtract(lat1).doubleValue());
        double deltaLonRad = Math.toRadians(lon2.subtract(lon1).doubleValue());

        double a = Math.sin(deltaLatRad / 2) * Math.sin(deltaLatRad / 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad)
                * Math.sin(deltaLonRad / 2) * Math.sin(deltaLonRad / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));

        return EARTH_RADIUS_KM * c;
    }
}
