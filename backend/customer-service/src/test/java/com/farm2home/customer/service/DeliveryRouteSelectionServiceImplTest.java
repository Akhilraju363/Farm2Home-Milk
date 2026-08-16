package com.farm2home.customer.service;

import com.farm2home.customer.client.DeliveryServiceClient;
import com.farm2home.customer.client.RouteDto;
import com.farm2home.customer.client.RoutePageDto;
import com.farm2home.customer.service.impl.DeliveryRouteSelectionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/** THE authoritative implementation this task introduces - see the class's own Javadoc for why it
 *  lives in customer-service rather than delivery-service (avoiding a circular HTTP dependency).
 *  Farm2Home's real configured location and route layout (see delivery-service's
 *  V6__add_route_geography.sql seed data): Central blankets the whole 10 KM delivery disc
 *  (radius 10.5 KM at the farm's own coordinates), with North/East/South/West centered 5 KM out
 *  in each direction (radius 7 KM each) winning the nearest-center tie-break for addresses
 *  actually closer to them. */
@ExtendWith(MockitoExtension.class)
class DeliveryRouteSelectionServiceImplTest {

    private static final BigDecimal FARM_LAT = new BigDecimal("13.62708825");
    private static final BigDecimal FARM_LON = new BigDecimal("78.96885066");

    @Mock private DeliveryServiceClient deliveryServiceClient;
    @InjectMocks private DeliveryRouteSelectionServiceImpl service;

    private RouteDto route(String code, String name, BigDecimal lat, BigDecimal lng, double radiusKm) {
        RouteDto r = new RouteDto();
        r.setId(UUID.randomUUID());
        r.setRouteCode(code);
        r.setRouteName(name);
        r.setCenterLatitude(lat);
        r.setCenterLongitude(lng);
        r.setRadiusKm(BigDecimal.valueOf(radiusKm));
        return r;
    }

    private List<RouteDto> fiveRealRoutes() {
        return List.of(
                route("F2H-CENTRAL", "Farm2Home Central", FARM_LAT, FARM_LON, 10.5),
                route("F2H-NORTH", "Farm2Home North", new BigDecimal("13.67199825"), FARM_LON, 7.0),
                route("F2H-EAST", "Farm2Home East", FARM_LAT, new BigDecimal("79.01506166"), 7.0),
                route("F2H-SOUTH", "Farm2Home South", new BigDecimal("13.58217825"), FARM_LON, 7.0),
                route("F2H-WEST", "Farm2Home West", FARM_LAT, new BigDecimal("78.92263966"), 7.0));
    }

    private void stubActiveRoutes(List<RouteDto> routes) {
        RoutePageDto page = new RoutePageDto();
        page.setContent(routes);
        when(deliveryServiceClient.getActiveRoutes()).thenReturn(Mono.just(page));
    }

    @Nested
    @DisplayName("directional selection (the real 5-route layout)")
    class DirectionalSelection {

        @Test
        @DisplayName("customer at the farm's exact location -> Central (nearest center)")
        void atFarmCenter_selectsCentral() {
            stubActiveRoutes(fiveRealRoutes());
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result = service.selectRoute(FARM_LAT, FARM_LON);
            assertThat(result).isPresent();
            assertThat(result.get().routeCode()).isEqualTo("F2H-CENTRAL");
        }

        @Test
        @DisplayName("customer close to the North route's own center -> North wins over Central")
        void nearNorthCenter_selectsNorth() {
            stubActiveRoutes(fiveRealRoutes());
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result =
                    service.selectRoute(new BigDecimal("13.67199825"), FARM_LON);
            assertThat(result).isPresent();
            assertThat(result.get().routeCode()).isEqualTo("F2H-NORTH");
        }

        @Test
        @DisplayName("customer close to the East route's own center -> East wins")
        void nearEastCenter_selectsEast() {
            stubActiveRoutes(fiveRealRoutes());
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result =
                    service.selectRoute(FARM_LAT, new BigDecimal("79.01506166"));
            assertThat(result).isPresent();
            assertThat(result.get().routeCode()).isEqualTo("F2H-EAST");
        }

        @Test
        @DisplayName("customer close to the South route's own center -> South wins")
        void nearSouthCenter_selectsSouth() {
            stubActiveRoutes(fiveRealRoutes());
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result =
                    service.selectRoute(new BigDecimal("13.58217825"), FARM_LON);
            assertThat(result).isPresent();
            assertThat(result.get().routeCode()).isEqualTo("F2H-SOUTH");
        }

        @Test
        @DisplayName("customer close to the West route's own center -> West wins")
        void nearWestCenter_selectsWest() {
            stubActiveRoutes(fiveRealRoutes());
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result =
                    service.selectRoute(FARM_LAT, new BigDecimal("78.92263966"));
            assertThat(result).isPresent();
            assertThat(result.get().routeCode()).isEqualTo("F2H-WEST");
        }

        @Test
        @DisplayName("customer exactly at the 10 KM delivery boundary still resolves to a route (Central's own margin)")
        void atTenKmBoundary_stillResolves() {
            stubActiveRoutes(fiveRealRoutes());
            // ~10 km due north of the farm (same trick as DeliveryAvailabilityServiceImplTest: a
            // shared longitude makes the along-meridian distance an exact R*theta calculation).
            double kmPerDegree = (6371.0 * Math.PI) / 180.0;
            BigDecimal latAt10Km = FARM_LAT.add(BigDecimal.valueOf(10.0 / kmPerDegree));
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result = service.selectRoute(latAt10Km, FARM_LON);
            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("customer well outside every route's coverage -> empty (logged as a coverage gap)")
        void farBeyondAllRoutes_returnsEmpty() {
            stubActiveRoutes(fiveRealRoutes());
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result =
                    service.selectRoute(new BigDecimal("20.0"), new BigDecimal("85.0"));
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("missing latitude -> empty, delivery-service never called")
        void missingLatitude_returnsEmptyWithoutCallingClient() {
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result = service.selectRoute(null, FARM_LON);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("missing longitude -> empty, delivery-service never called")
        void missingLongitude_returnsEmptyWithoutCallingClient() {
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result = service.selectRoute(FARM_LAT, null);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("out-of-range latitude (> 90) -> empty, delivery-service never called")
        void invalidLatitude_returnsEmpty() {
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result =
                    service.selectRoute(new BigDecimal("95"), FARM_LON);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("out-of-range longitude (< -180) -> empty, delivery-service never called")
        void invalidLongitude_returnsEmpty() {
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result =
                    service.selectRoute(FARM_LAT, new BigDecimal("-200"));
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("a route missing geo fields is never selectable, even if it would otherwise be nearest")
        void routeMissingGeoFields_neverSelected() {
            RouteDto noGeo = route("F2H-LEGACY", "Legacy Route", null, null, 0);
            noGeo.setRadiusKm(null);
            stubActiveRoutes(List.of(noGeo, route("F2H-CENTRAL", "Farm2Home Central", FARM_LAT, FARM_LON, 10.5)));

            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result = service.selectRoute(FARM_LAT, FARM_LON);

            assertThat(result).isPresent();
            assertThat(result.get().routeCode()).isEqualTo("F2H-CENTRAL");
        }

        @Test
        @DisplayName("no active routes at all -> empty")
        void noActiveRoutes_returnsEmpty() {
            stubActiveRoutes(List.of());
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result = service.selectRoute(FARM_LAT, FARM_LON);
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("delivery-service unreachable -> empty, not a hard failure")
        void deliveryServiceUnreachable_returnsEmpty() {
            when(deliveryServiceClient.getActiveRoutes()).thenReturn(Mono.error(new WebClientRequestException(
                    new IOException("connection refused"), HttpMethod.GET,
                    URI.create("http://delivery-service/api/v1/delivery/routes/search"), new HttpHeaders())));

            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result = service.selectRoute(FARM_LAT, FARM_LON);

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("overlapping routes / deterministic tie-break")
    class TieBreak {

        @Test
        @DisplayName("two routes at the exact same distance -> lower routeCode wins, deterministically")
        void equidistantRoutes_lowerCodeWins() {
            // Both centered exactly at the customer's own point - distance 0 for both, so only the
            // tie-break (routeCode ascending) can decide the outcome.
            RouteDto routeB = route("RT-B", "Route B", FARM_LAT, FARM_LON, 5.0);
            RouteDto routeA = route("RT-A", "Route A", FARM_LAT, FARM_LON, 5.0);
            stubActiveRoutes(List.of(routeB, routeA));

            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result = service.selectRoute(FARM_LAT, FARM_LON);

            assertThat(result).isPresent();
            assertThat(result.get().routeCode()).isEqualTo("RT-A");
        }

        @Test
        @DisplayName("closer-but-narrower route still wins over a farther-but-wider route that also covers the point")
        void nearestCoveringRoute_wins_notWidestRoute() {
            // Central (wide, far center) and North (narrow, close center) both cover this point -
            // nearest center wins, matching the documented priority (not "widest radius").
            stubActiveRoutes(fiveRealRoutes());
            Optional<DeliveryRouteSelectionServiceImpl.SelectedRoute> result =
                    service.selectRoute(new BigDecimal("13.65"), FARM_LON);
            assertThat(result).isPresent();
            assertThat(result.get().routeCode()).isEqualTo("F2H-NORTH");
        }
    }
}
