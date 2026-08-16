package com.farm2home.common.core.geo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class GeoDistanceUtilTest {

    private static final BigDecimal ORIGIN_LAT = BigDecimal.ZERO;
    private static final BigDecimal ORIGIN_LON = BigDecimal.ZERO;

    @Test
    @DisplayName("same point -> zero distance")
    void samePoint_zeroDistance() {
        double distance = GeoDistanceUtil.haversineKm(ORIGIN_LAT, ORIGIN_LON, ORIGIN_LAT, ORIGIN_LON);
        assertThat(distance).isEqualTo(0.0);
    }

    @Test
    @DisplayName("due-north point at a known angular offset -> distance matches R * theta exactly (identity when longitude is unchanged)")
    void dueNorth_matchesKnownDistance() {
        // For two points sharing a longitude, Haversine reduces to R * deltaLatRadians exactly -
        // a convenient, deterministic way to assert a known distance without depending on any
        // external reference calculation.
        double targetKm = 111.19;
        double deltaLatDeg = (targetKm * 180.0) / (6371.0 * Math.PI);
        BigDecimal customerLat = BigDecimal.valueOf(deltaLatDeg);

        double distance = GeoDistanceUtil.haversineKm(ORIGIN_LAT, ORIGIN_LON, customerLat, ORIGIN_LON);

        assertThat(distance).isCloseTo(targetKm, within(0.001));
    }

    @Test
    @DisplayName("known real-world distance: Farm2Home origin to a point ~7km away stays under 10km")
    void knownNearbyDistance_isReasonable() {
        BigDecimal farmLat = new BigDecimal("13.62708825");
        BigDecimal farmLon = new BigDecimal("78.96885066");
        // ~0.063 degrees latitude north ≈ 7 km (111.19 km/degree)
        BigDecimal nearbyLat = new BigDecimal("13.69");
        BigDecimal nearbyLon = farmLon;

        double distance = GeoDistanceUtil.haversineKm(farmLat, farmLon, nearbyLat, nearbyLon);

        assertThat(distance).isBetween(6.0, 8.0);
    }

    @Test
    @DisplayName("distance is symmetric: A→B equals B→A")
    void isSymmetric() {
        BigDecimal lat2 = new BigDecimal("14.0");
        BigDecimal lon2 = new BigDecimal("79.5");

        double ab = GeoDistanceUtil.haversineKm(ORIGIN_LAT, ORIGIN_LON, lat2, lon2);
        double ba = GeoDistanceUtil.haversineKm(lat2, lon2, ORIGIN_LAT, ORIGIN_LON);

        assertThat(ab).isCloseTo(ba, within(1e-9));
    }
}
