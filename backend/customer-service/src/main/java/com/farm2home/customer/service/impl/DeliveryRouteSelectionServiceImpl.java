package com.farm2home.customer.service.impl;

import com.farm2home.common.core.geo.GeoDistanceUtil;
import com.farm2home.customer.client.DeliveryServiceClient;
import com.farm2home.customer.client.RouteDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientException;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * THE single authoritative implementation of automatic delivery-route selection - order-service
 * and the frontend both consume its result (via DeliveryAvailabilityResponse) rather than each
 * running their own copy of this logic. Lives here (not in delivery-service, which owns the
 * DeliveryRoute data) specifically to avoid a circular HTTP dependency: delivery-service already
 * calls order-service (OrderServiceClient, used by manualAssign), so order-service calling
 * delivery-service directly for route selection would close that loop. customer-service already
 * sits between them for the existing delivery-eligibility check (order-service ->
 * customer-service -> farm-service), and delivery-service has no dependency back into
 * customer-service, so adding customer-service -> delivery-service here is a clean, one-directional
 * edge - see DeliveryServiceClient's own Javadoc.
 *
 * Algorithm: among ACTIVE routes with a fully-configured coverage circle (centerLatitude/
 * centerLongitude/radiusKm all present - see DeliveryRoute's own Javadoc on why a route may
 * legitimately lack these), select the one whose center is nearest to the customer's location AND
 * within that route's own radiusKm (never a route the point actually falls outside of, even if it
 * happens to be the closest center). Ties (identical distance) are broken by routeCode, ascending -
 * deterministic regardless of database row order.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryRouteSelectionServiceImpl {

    private final DeliveryServiceClient deliveryServiceClient;

    public record SelectedRoute(java.util.UUID routeId, String routeName, String routeCode) {
    }

    /** Returns empty when: coordinates are missing/invalid, delivery-service could not be reached
     *  (treated as "unknown", logged - never a hard failure, matching this codebase's established
     *  "a transient cross-service hiccup never bricks the caller" convention), or no active,
     *  fully-configured route's coverage circle actually contains the point - the last case is a
     *  genuine route-coverage gap and is logged at ERROR so an administrator notices it (see
     *  DeliveryRoute seed migration for how the initial 5 routes are deliberately sized to avoid
     *  this ever happening for an address that already passed the 10 KM eligibility check). */
    public Optional<SelectedRoute> selectRoute(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        if (latitude.compareTo(BigDecimal.valueOf(-90)) < 0 || latitude.compareTo(BigDecimal.valueOf(90)) > 0
                || longitude.compareTo(BigDecimal.valueOf(-180)) < 0 || longitude.compareTo(BigDecimal.valueOf(180)) > 0) {
            log.warn("Ignoring out-of-range coordinates for route selection: lat={}, lng={}", latitude, longitude);
            return Optional.empty();
        }

        List<RouteDto> routes;
        try {
            var page = deliveryServiceClient.getActiveRoutes().block();
            routes = page != null && page.getContent() != null ? page.getContent() : List.of();
        } catch (WebClientException ex) {
            log.warn("Could not reach delivery-service for route selection: {}", ex.getMessage());
            return Optional.empty();
        }

        Optional<SelectedRoute> selected = routes.stream()
                .filter(r -> r.getCenterLatitude() != null && r.getCenterLongitude() != null && r.getRadiusKm() != null)
                .map(r -> new RouteCandidate(r, GeoDistanceUtil.haversineKm(latitude, longitude, r.getCenterLatitude(), r.getCenterLongitude())))
                .filter(c -> c.distanceKm <= c.route.getRadiusKm().doubleValue())
                .min(Comparator.<RouteCandidate>comparingDouble(c -> c.distanceKm)
                        .thenComparing(c -> c.route.getRouteCode()))
                .map(c -> new SelectedRoute(c.route.getId(), c.route.getRouteName(), c.route.getRouteCode()));

        if (selected.isEmpty()) {
            log.error("No active delivery route covers location lat={}, lng={} - check route coverage configuration.",
                    latitude, longitude);
        }
        return selected;
    }

    private record RouteCandidate(RouteDto route, double distanceKm) {
    }
}
