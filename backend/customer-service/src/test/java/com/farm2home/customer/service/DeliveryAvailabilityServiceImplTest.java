package com.farm2home.customer.service;

import com.farm2home.customer.client.BusinessSettingsDto;
import com.farm2home.customer.client.FarmServiceClient;
import com.farm2home.customer.config.UserPrincipal;
import com.farm2home.customer.domain.entity.CustomerAddress;
import com.farm2home.customer.domain.repository.CustomerAddressRepository;
import com.farm2home.customer.dto.response.DeliveryAvailabilityResponse;
import com.farm2home.customer.exception.CustomerException;
import com.farm2home.customer.exception.ResourceNotFoundException;
import com.farm2home.customer.service.impl.DeliveryAvailabilityServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Origin is fixed at (0,0) and the customer address is placed due north of it, since two points
 *  sharing a longitude make Haversine reduce to an exact R*theta identity (see
 *  GeoDistanceUtilTest) - lets every boundary case below assert a deterministic distanceKm rather
 *  than an approximation. */
@ExtendWith(MockitoExtension.class)
class DeliveryAvailabilityServiceImplTest {

    private static final BigDecimal FARM_LAT = BigDecimal.ZERO;
    private static final BigDecimal FARM_LON = BigDecimal.ZERO;
    private static final BigDecimal RADIUS_KM = new BigDecimal("10");
    private static final double KM_PER_DEGREE = (6371.0 * Math.PI) / 180.0;

    @Mock private CustomerAddressRepository addressRepository;
    @Mock private FarmServiceClient farmServiceClient;
    // Unstubbed by default in every test below - Mockito's default answer for an
    // Optional-returning method is Optional.empty(), which is exactly "no route selected", so
    // these existing eligibility-only tests don't need to care about route selection at all.
    @Mock private com.farm2home.customer.service.impl.DeliveryRouteSelectionServiceImpl routeSelectionService;
    @InjectMocks private DeliveryAvailabilityServiceImpl service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID addressId = UUID.randomUUID();

    @BeforeEach
    void setupBusinessSettings() {
        BusinessSettingsDto settings = new BusinessSettingsDto();
        settings.setFarmLatitude(FARM_LAT);
        settings.setFarmLongitude(FARM_LON);
        settings.setDeliveryRadiusKm(RADIUS_KM);
        lenient().when(farmServiceClient.getBusinessSettings()).thenReturn(Mono.just(settings));
    }

    private BigDecimal latForDistanceKm(double distanceKm) {
        return BigDecimal.valueOf(distanceKm / KM_PER_DEGREE);
    }

    private CustomerAddress addressAtDistance(double distanceKm) {
        return CustomerAddress.builder()
                .id(addressId)
                .customerId(customerId)
                .latitude(latForDistanceKm(distanceKm))
                .longitude(FARM_LON)
                .defaultAddress(true)
                .deleted(false)
                .build();
    }

    @Nested
    @DisplayName("distance boundary cases (distanceKm <= deliveryRadiusKm = eligible)")
    class BoundaryCases {

        @ParameterizedTest(name = "{0} km away -> eligible")
        @CsvSource({"5.0", "9.99", "10.0"})
        @DisplayName("at or under the radius -> deliveryAvailable = true")
        void withinOrAtRadius_eligible(double distanceKm) {
            when(addressRepository.findByCustomerIdAndDefaultAddressTrueAndDeletedFalse(customerId))
                    .thenReturn(Optional.of(addressAtDistance(distanceKm)));

            DeliveryAvailabilityResponse response = service.getForCustomer(customerId, null);

            assertThat(response.getDeliveryAvailable()).isTrue();
            assertThat(response.getDistanceKm()).isCloseTo(BigDecimal.valueOf(distanceKm), org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));
            assertThat(response.getDeliveryRadiusKm()).isEqualByComparingTo("10");
            assertThat(response.getMessage()).isNull();
        }

        @ParameterizedTest(name = "{0} km away -> not eligible")
        @CsvSource({"10.01", "15.0"})
        @DisplayName("beyond the radius -> deliveryAvailable = false")
        void beyondRadius_notEligible(double distanceKm) {
            when(addressRepository.findByCustomerIdAndDefaultAddressTrueAndDeletedFalse(customerId))
                    .thenReturn(Optional.of(addressAtDistance(distanceKm)));

            DeliveryAvailabilityResponse response = service.getForCustomer(customerId, null);

            assertThat(response.getDeliveryAvailable()).isFalse();
            assertThat(response.getDistanceKm()).isCloseTo(BigDecimal.valueOf(distanceKm), org.assertj.core.data.Offset.offset(new BigDecimal("0.01")));
            assertThat(response.getMessage()).isEqualTo("Delivery is currently unavailable at this location.");
        }
    }

    @Nested
    @DisplayName("edge cases")
    class EdgeCases {

        @Test
        @DisplayName("no address at all -> deliveryAvailable is null (never assumed available), radius is still reported")
        void noAddress_unknown() {
            when(addressRepository.findByCustomerIdAndDefaultAddressTrueAndDeletedFalse(customerId))
                    .thenReturn(Optional.empty());

            DeliveryAvailabilityResponse response = service.getForCustomer(customerId, null);

            assertThat(response.getDeliveryAvailable()).isNull();
            assertThat(response.getDistanceKm()).isNull();
            assertThat(response.getDeliveryRadiusKm()).isEqualByComparingTo("10");
            assertThat(response.getMessage()).isEqualTo("No delivery address is set for this customer.");
        }

        @Test
        @DisplayName("address exists but has no captured coordinates -> deliveryAvailable is null, never assumed available")
        void missingCoordinates_unknown() {
            CustomerAddress address = CustomerAddress.builder()
                    .id(addressId).customerId(customerId).defaultAddress(true).deleted(false)
                    .latitude(null).longitude(null)
                    .build();
            when(addressRepository.findByCustomerIdAndDefaultAddressTrueAndDeletedFalse(customerId))
                    .thenReturn(Optional.of(address));

            DeliveryAvailabilityResponse response = service.getForCustomer(customerId, null);

            assertThat(response.getDeliveryAvailable()).isNull();
            assertThat(response.getMessage()).isEqualTo("Delivery availability cannot be determined for this address.");
        }

        @Test
        @DisplayName("a soft-deleted address requested by id is treated as not found, not a stale result")
        void deletedAddress_treatedAsNoAddress() {
            when(addressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId))
                    .thenReturn(Optional.empty());

            DeliveryAvailabilityResponse response = service.getForCustomer(customerId, addressId);

            assertThat(response.getDeliveryAvailable()).isNull();
            assertThat(response.getMessage()).isEqualTo("No delivery address is set for this customer.");
        }

        @Test
        @DisplayName("farm-service unreachable -> CustomerException, never silently assumes available")
        void businessSettingsUnreachable_throwsCustomerException() {
            when(farmServiceClient.getBusinessSettings())
                    .thenReturn(Mono.error(new WebClientRequestException(
                            new IOException("connection refused"), HttpMethod.GET,
                            URI.create("http://farm-service/api/v1/farm/business-settings"),
                            new HttpHeaders())));

            assertThatThrownBy(() -> service.getForCustomer(customerId, null))
                    .isInstanceOf(CustomerException.class);
        }

        @Test
        @DisplayName("farm-service returns incomplete settings -> CustomerException")
        void incompleteBusinessSettings_throwsCustomerException() {
            BusinessSettingsDto incomplete = new BusinessSettingsDto();
            when(farmServiceClient.getBusinessSettings()).thenReturn(Mono.just(incomplete));

            assertThatThrownBy(() -> service.getForCustomer(customerId, null))
                    .isInstanceOf(CustomerException.class);
        }
    }

    @Nested
    @DisplayName("route selection integration")
    class RouteSelectionIntegration {

        @Test
        @DisplayName("eligible address with a matched route -> routeId/routeName populated on the response")
        void eligibleWithRoute_populatesRouteFields() {
            when(addressRepository.findByCustomerIdAndDefaultAddressTrueAndDeletedFalse(customerId))
                    .thenReturn(Optional.of(addressAtDistance(5.0)));
            UUID routeId = UUID.randomUUID();
            when(routeSelectionService.selectRoute(any(), any())).thenReturn(
                    Optional.of(new com.farm2home.customer.service.impl.DeliveryRouteSelectionServiceImpl.SelectedRoute(
                            routeId, "Farm2Home Central", "F2H-CENTRAL")));

            DeliveryAvailabilityResponse response = service.getForCustomer(customerId, null);

            assertThat(response.getDeliveryAvailable()).isTrue();
            assertThat(response.getRouteId()).isEqualTo(routeId);
            assertThat(response.getRouteName()).isEqualTo("Farm2Home Central");
        }

        @Test
        @DisplayName("eligible address but no route covers it (coverage gap) -> routeId/routeName stay null, still eligible")
        void eligibleButNoRouteCoverage_leavesRouteFieldsNull() {
            when(addressRepository.findByCustomerIdAndDefaultAddressTrueAndDeletedFalse(customerId))
                    .thenReturn(Optional.of(addressAtDistance(5.0)));
            when(routeSelectionService.selectRoute(any(), any())).thenReturn(Optional.empty());

            DeliveryAvailabilityResponse response = service.getForCustomer(customerId, null);

            assertThat(response.getDeliveryAvailable()).isTrue();
            assertThat(response.getRouteId()).isNull();
            assertThat(response.getRouteName()).isNull();
        }

        @Test
        @DisplayName("out-of-radius address -> route selection is never even attempted")
        void outOfRadius_routeSelectionNeverAttempted() {
            when(addressRepository.findByCustomerIdAndDefaultAddressTrueAndDeletedFalse(customerId))
                    .thenReturn(Optional.of(addressAtDistance(15.0)));

            DeliveryAvailabilityResponse response = service.getForCustomer(customerId, null);

            assertThat(response.getDeliveryAvailable()).isFalse();
            assertThat(response.getRouteId()).isNull();
            org.mockito.Mockito.verify(routeSelectionService, org.mockito.Mockito.never()).selectRoute(any(), any());
        }
    }

    @Nested
    @DisplayName("getForCustomerScoped() - ownership")
    class Ownership {

        @Test
        @DisplayName("customer checking their own availability -> allowed")
        void selfCheck_allowed() {
            UserPrincipal principal = new UserPrincipal(customerId, "9800000001", Set.of("CUSTOMER"));
            when(addressRepository.findByCustomerIdAndDefaultAddressTrueAndDeletedFalse(customerId))
                    .thenReturn(Optional.of(addressAtDistance(5.0)));

            DeliveryAvailabilityResponse response = service.getForCustomerScoped(customerId, null, principal);

            assertThat(response.getDeliveryAvailable()).isTrue();
        }

        @Test
        @DisplayName("non-admin checking a different customer's availability -> 404, same shape as an unknown id")
        void otherCustomer_notAdmin_throwsNotFound() {
            UserPrincipal principal = new UserPrincipal(UUID.randomUUID(), "9800000002", Set.of("CUSTOMER"));

            assertThatThrownBy(() -> service.getForCustomerScoped(customerId, null, principal))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("admin (e.g. order-service on behalf of an admin-created order) can check any customer's availability")
        void admin_canCheckAnyCustomer() {
            UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), "9800000099", Set.of("SUPER_ADMIN"));
            when(addressRepository.findByCustomerIdAndDefaultAddressTrueAndDeletedFalse(customerId))
                    .thenReturn(Optional.of(addressAtDistance(5.0)));

            DeliveryAvailabilityResponse response = service.getForCustomerScoped(customerId, null, admin);

            assertThat(response.getDeliveryAvailable()).isTrue();
        }
    }
}
