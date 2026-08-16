package com.farm2home.delivery.service.impl;

import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * THE single authoritative "which delivery partner should get this order" implementation - both
 * {@link com.farm2home.delivery.kafka.OrderEventConsumer} (automatic assignment) and any future
 * caller must go through here rather than re-implementing the eligibility/workload/tie-break
 * rules. Manual assignment ({@link DeliveryAssignmentServiceImpl#manualAssign}) deliberately does
 * NOT call this - an admin picks any partner explicitly, by design (this task adds automatic
 * assignment as an additional workflow, not a replacement).
 *
 * <p><b>Eligibility</b>: {@code active = true}, {@code deleted = false}, and {@code route}
 * matching the order's own {@code deliveryRouteId} exactly - a partner on a different route is
 * never selected here (an admin doing a manual cross-route override is the only sanctioned way
 * that happens, see manualAssign's routeId override).
 *
 * <p><b>Workload</b>: count of the partner's non-terminal (ASSIGNED/OUT_FOR_DELIVERY)
 * assignments - DELIVERED/FAILED never count, matching the existing DeliveryPartnerRepository
 * convention this class inherits rather than re-derives. Not a persisted field anywhere; always
 * computed live from {@link DeliveryAssignmentRepository}, so it can never drift from the
 * assignments it counts.
 *
 * <p><b>Tie-break</b>: lowest workload wins; ties broken by {@code DeliveryPartner.id} ascending -
 * deterministic regardless of database row order or which candidate happens to be examined first.
 *
 * <p><b>Concurrency</b>: two orders on the same route arriving at (nearly) the same time must
 * never both land on the same "currently least-loaded" partner just because both read the
 * workload before either committed. {@link DeliveryPartnerRepository#findEligiblePartnersForRouteForUpdate}
 * takes a {@code PESSIMISTIC_WRITE} lock on every eligible partner row for the route, so a second
 * concurrent call for the same route blocks at that line until the first call's transaction
 * commits (making its new assignment - and the resulting workload increase - visible) or rolls
 * back. This is the standard "lock the candidate set, then decide" pattern, the same spirit as
 * inventory-service's atomic stock decrement (a single UPDATE...WHERE there; a row lock here,
 * because the decision itself - which of several rows to pick - can't be expressed as a single
 * UPDATE). Callers MUST invoke this within a transaction (see {@code @Transactional} below) so the
 * lock is actually held until the resulting {@code DeliveryAssignment} is saved and committed -
 * calling it standalone and committing separately would defeat the whole point.
 */
@Service
@RequiredArgsConstructor
public class PartnerSelectionServiceImpl {

    private static final List<AssignmentStatus> ACTIVE_ASSIGNMENT_STATUSES =
            List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.OUT_FOR_DELIVERY);

    private final DeliveryPartnerRepository partnerRepository;
    private final DeliveryAssignmentRepository assignmentRepository;

    @Transactional
    public Optional<DeliveryPartner> selectLeastLoadedPartner(UUID routeId) {
        if (routeId == null) {
            return Optional.empty();
        }

        List<DeliveryPartner> candidates = partnerRepository.findEligiblePartnersForRouteForUpdate(routeId);
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        return candidates.stream()
                .min(Comparator
                        .<DeliveryPartner>comparingLong(this::activeWorkload)
                        .thenComparing(DeliveryPartner::getId));
    }

    private long activeWorkload(DeliveryPartner partner) {
        return assignmentRepository.countByDeliveryPartner_IdAndStatusIn(partner.getId(), ACTIVE_ASSIGNMENT_STATUSES);
    }
}
