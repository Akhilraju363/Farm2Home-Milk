package com.farm2home.delivery.service.impl;

import com.farm2home.delivery.config.UserPrincipal;
import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryLocation;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryLocationRepository;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import com.farm2home.delivery.dto.request.SubmitLocationRequest;
import com.farm2home.delivery.dto.response.LocationResponse;
import com.farm2home.delivery.exception.DeliveryException;
import com.farm2home.delivery.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * Location submission is only accepted while an assignment is OUT_FOR_DELIVERY - not ASSIGNED
 * (no active movement yet) and not DELIVERED/FAILED (tracking stopped, matching the "no live
 * marker forever" and "no delivery-partner location exposed indefinitely" requirements). Reading
 * back a current/historical location follows the same OUT_FOR_DELIVERY-or-later rule implicitly:
 * a location row simply never exists until the first accepted submission.
 *
 * recordedAt is always server-assigned (LocalDateTime.now() at persist time, not any
 * client-supplied timestamp) - this alone guarantees each new row is chronologically after the
 * previous one for the same assignment, which is what "reject an older event" (duplicate/
 * out-of-order/retry handling) actually needs, without extra conflict-resolution logic.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DeliveryLocationServiceImpl {

    /** Matches Phase 14's suggested thresholds exactly. */
    private static final Duration LIVE_THRESHOLD = Duration.ofMinutes(1);
    private static final Duration RECENT_THRESHOLD = Duration.ofMinutes(5);

    private final DeliveryLocationRepository locationRepository;
    private final DeliveryAssignmentRepository assignmentRepository;
    private final DeliveryPartnerRepository partnerRepository;

    @Transactional
    public LocationResponse submitLocation(UUID assignmentId, SubmitLocationRequest request, UUID callerId) {
        UUID partnerId = resolvePartnerId(callerId);
        DeliveryAssignment assignment = assignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partnerId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

        if (assignment.getStatus() != AssignmentStatus.OUT_FOR_DELIVERY) {
            throw new DeliveryException(
                    "Location can only be submitted while the assignment is OUT_FOR_DELIVERY (currently "
                            + assignment.getStatus() + ").");
        }

        validateCoordinate("latitude", request.getLatitude(), -90.0, 90.0);
        validateCoordinate("longitude", request.getLongitude(), -180.0, 180.0);

        DeliveryLocation saved = locationRepository.save(DeliveryLocation.builder()
                .deliveryAssignmentId(assignmentId)
                .deliveryPartnerId(partnerId)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                .accuracy(request.getAccuracy())
                .speed(request.getSpeed())
                .heading(request.getHeading())
                .recordedAt(LocalDateTime.now())
                .build());

        log.debug("Recorded location for assignment {}: ({}, {})", assignmentId, saved.getLatitude(), saved.getLongitude());
        return toResponse(saved);
    }

    public Optional<LocationResponse> getCurrentLocation(UUID assignmentId, UserPrincipal principal) {
        DeliveryAssignment assignment = resolveForRead(assignmentId, principal);
        // No live marker after delivery completes/fails - a customer re-opening a completed
        // order's tracking page should see "tracking has ended", not a frozen last-known pin.
        if (assignment.getStatus().isTerminal()) {
            return Optional.empty();
        }
        return locationRepository.findTopByDeliveryAssignmentIdOrderByRecordedAtDesc(assignmentId).map(this::toResponse);
    }

    public Page<LocationResponse> getHistory(UUID assignmentId, UserPrincipal principal, Pageable pageable) {
        resolveForRead(assignmentId, principal);
        return locationRepository.findAllByDeliveryAssignmentIdOrderByRecordedAtDesc(assignmentId, pageable).map(this::toResponse);
    }

    /** Admin (FARM_MANAGER/SUPER_ADMIN/DELIVERY_MANAGER, per UserPrincipal.isAdmin()) may read any
     *  assignment's tracking. A DELIVERY_PARTNER may read only their own. A CUSTOMER may read only
     *  the assignment for their own order (assignment.customerId, now reliably populated for both
     *  auto- and manually-created assignments - see DeliveryAssignmentServiceImpl.manualAssign).
     *  Everyone else (and a mismatched id for any of the above) gets 404, not 403, so a caller can
     *  never confirm another customer's/partner's assignment exists. */
    private DeliveryAssignment resolveForRead(UUID assignmentId, UserPrincipal principal) {
        DeliveryAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Assignment not found: " + assignmentId));

        if (principal.isAdmin()) {
            return assignment;
        }
        if (principal.isDeliveryPartner()) {
            UUID partnerId = resolvePartnerId(principal.userId());
            if (assignment.getDeliveryPartner() != null && partnerId.equals(assignment.getDeliveryPartner().getId())) {
                return assignment;
            }
            throw new ResourceNotFoundException("Assignment not found: " + assignmentId);
        }
        // CUSTOMER (or any other role): only their own order's assignment.
        if (assignment.getCustomerId() != null && assignment.getCustomerId().equals(principal.userId())) {
            return assignment;
        }
        throw new ResourceNotFoundException("Assignment not found: " + assignmentId);
    }

    private UUID resolvePartnerId(UUID callerId) {
        return partnerRepository.findByUserIdAndDeletedFalse(callerId)
                .orElseThrow(() -> new ResourceNotFoundException("No delivery partner profile for user: " + callerId))
                .getId();
    }

    private void validateCoordinate(String field, Double value, double min, double max) {
        if (value == null || value.isNaN() || value.isInfinite()) {
            throw new DeliveryException(field + " must be a finite number");
        }
        if (value < min || value > max) {
            throw new DeliveryException(field + " must be between " + min + " and " + max);
        }
    }

    private LocationResponse toResponse(DeliveryLocation location) {
        return LocationResponse.builder()
                .deliveryAssignmentId(location.getDeliveryAssignmentId())
                .latitude(location.getLatitude())
                .longitude(location.getLongitude())
                .accuracy(location.getAccuracy())
                .speed(location.getSpeed())
                .heading(location.getHeading())
                .recordedAt(location.getRecordedAt())
                .freshness(freshnessOf(location.getRecordedAt()))
                .build();
    }

    private String freshnessOf(LocalDateTime recordedAt) {
        Duration age = Duration.between(recordedAt, LocalDateTime.now());
        if (age.compareTo(LIVE_THRESHOLD) <= 0) return "LIVE";
        if (age.compareTo(RECENT_THRESHOLD) <= 0) return "RECENT";
        return "STALE";
    }
}
