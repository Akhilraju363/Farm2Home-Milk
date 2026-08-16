package com.farm2home.customer.service.impl;

import com.farm2home.common.core.geo.GeoDistanceUtil;
import com.farm2home.customer.client.BusinessSettingsDto;
import com.farm2home.customer.client.FarmServiceClient;
import com.farm2home.customer.config.UserPrincipal;
import com.farm2home.customer.domain.entity.CustomerAddress;
import com.farm2home.customer.domain.repository.CustomerAddressRepository;
import com.farm2home.customer.dto.response.DeliveryAvailabilityResponse;
import com.farm2home.customer.exception.CustomerException;
import com.farm2home.customer.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClientException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.UUID;

/**
 * Backend-authoritative delivery-eligibility check: Haversine distance from Farm2Home's business
 * origin (farm-service) to the customer's chosen address, compared against the configured
 * delivery radius. Google Maps is visualization only - this is the single real calculation, and
 * order-service reuses this exact endpoint (via GET /me/delivery-availability, header-forwarded)
 * for order-creation revalidation rather than re-implementing the distance math itself.
 */
@Service
@RequiredArgsConstructor
public class DeliveryAvailabilityServiceImpl {

    private static final String NO_ADDRESS_MESSAGE = "No delivery address is set for this customer.";
    private static final String UNKNOWN_COORDINATES_MESSAGE = "Delivery availability cannot be determined for this address.";
    private static final String UNAVAILABLE_MESSAGE = "Delivery is currently unavailable at this location.";

    private final CustomerAddressRepository addressRepository;
    private final FarmServiceClient farmServiceClient;
    private final DeliveryRouteSelectionServiceImpl routeSelectionService;

    /** Ownership-scoped counterpart to {@link #getForCustomer(UUID, UUID)} - callers may only
     *  check their own delivery availability unless they hold an admin authority. Used by
     *  order-service's CustomerServiceClient (via header-forwarding) so an order created by an
     *  admin on behalf of a different customer is validated against THAT customer's address, not
     *  the admin's own. Same 404-not-403 ownership shape as CustomerServiceImpl.getCustomerScoped. */
    @Transactional(readOnly = true)
    public DeliveryAvailabilityResponse getForCustomerScoped(UUID customerId, UUID addressId, UserPrincipal principal) {
        if (!principal.isAdmin() && !customerId.equals(principal.userId())) {
            throw new ResourceNotFoundException("Customer not found: " + customerId);
        }
        return getForCustomer(customerId, addressId);
    }

    /** addressId null -> the customer's default address. */
    @Transactional(readOnly = true)
    public DeliveryAvailabilityResponse getForCustomer(UUID customerId, UUID addressId) {
        Optional<CustomerAddress> address = addressId != null
                ? addressRepository.findByIdAndCustomerIdAndDeletedFalse(addressId, customerId)
                : addressRepository.findByCustomerIdAndDefaultAddressTrueAndDeletedFalse(customerId);

        BusinessSettingsDto settings = fetchBusinessSettings();
        BigDecimal radius = settings.getDeliveryRadiusKm();

        if (address.isEmpty()) {
            return unknown(radius, NO_ADDRESS_MESSAGE);
        }
        CustomerAddress a = address.get();
        if (a.getLatitude() == null || a.getLongitude() == null) {
            return unknown(radius, UNKNOWN_COORDINATES_MESSAGE);
        }

        double distance = GeoDistanceUtil.haversineKm(
                settings.getFarmLatitude(), settings.getFarmLongitude(), a.getLatitude(), a.getLongitude());
        BigDecimal distanceKm = BigDecimal.valueOf(distance).setScale(2, RoundingMode.HALF_UP);
        boolean available = distanceKm.compareTo(radius) <= 0;

        // Route selection only runs for an address that's actually eligible - there's no route to
        // pick for an address Farm2Home can't deliver to at all, and it would be wasted work.
        var route = available ? routeSelectionService.selectRoute(a.getLatitude(), a.getLongitude()) : java.util.Optional.<DeliveryRouteSelectionServiceImpl.SelectedRoute>empty();

        return DeliveryAvailabilityResponse.builder()
                .deliveryAvailable(available)
                .distanceKm(distanceKm)
                .deliveryRadiusKm(radius)
                .message(available ? null : UNAVAILABLE_MESSAGE)
                .routeId(route.map(DeliveryRouteSelectionServiceImpl.SelectedRoute::routeId).orElse(null))
                .routeName(route.map(DeliveryRouteSelectionServiceImpl.SelectedRoute::routeName).orElse(null))
                .build();
    }

    private DeliveryAvailabilityResponse unknown(BigDecimal radius, String message) {
        return DeliveryAvailabilityResponse.builder()
                .deliveryAvailable(null)
                .distanceKm(null)
                .deliveryRadiusKm(radius)
                .message(message)
                .build();
    }

    private BusinessSettingsDto fetchBusinessSettings() {
        try {
            BusinessSettingsDto settings = farmServiceClient.getBusinessSettings().block();
            if (settings == null || settings.getFarmLatitude() == null || settings.getFarmLongitude() == null
                    || settings.getDeliveryRadiusKm() == null) {
                throw new CustomerException("Could not verify delivery availability right now. Please try again.");
            }
            return settings;
        } catch (WebClientException ex) {
            throw new CustomerException("Could not verify delivery availability right now. Please try again.");
        }
    }
}
