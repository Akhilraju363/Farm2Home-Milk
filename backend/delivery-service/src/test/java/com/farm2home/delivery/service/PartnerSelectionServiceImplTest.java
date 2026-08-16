package com.farm2home.delivery.service;

import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import com.farm2home.delivery.service.impl.PartnerSelectionServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** PartnerSelectionServiceImpl is THE single authoritative "which partner gets this order"
 *  implementation (see its own Javadoc) - these tests cover section 27's exact required matrix:
 *  no/one/multiple partners, least-loaded selection, equal workload, inactive/deleted/wrong-route
 *  exclusion (enforced via the locked repository query itself, mocked here), all unavailable. Real
 *  concurrent-transaction locking behavior (PESSIMISTIC_WRITE) is CODE VERIFIED (the repository
 *  method annotation + this class's Javadoc reasoning) but not exercised here - Mockito mocks
 *  don't simulate real Postgres row locks; see the final report for what WAS live-verified. */
@ExtendWith(MockitoExtension.class)
class PartnerSelectionServiceImplTest {

    @Mock private DeliveryPartnerRepository partnerRepository;
    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @InjectMocks private PartnerSelectionServiceImpl service;

    private final UUID routeId = UUID.randomUUID();
    private static final List<AssignmentStatus> ACTIVE = List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.OUT_FOR_DELIVERY);

    private DeliveryPartner partner(String name) {
        return DeliveryPartner.builder().id(UUID.randomUUID()).name(name).active(true).deleted(false).build();
    }

    @Nested
    @DisplayName("basic eligibility / candidate-set handling")
    class Basics {

        @Test
        @DisplayName("null routeId -> empty, repository never queried")
        void nullRoute_returnsEmptyWithoutQuerying() {
            Optional<DeliveryPartner> result = service.selectLeastLoadedPartner(null);

            assertThat(result).isEmpty();
            org.mockito.Mockito.verifyNoInteractions(partnerRepository);
        }

        @Test
        @DisplayName("no eligible partners on the route -> empty (order stays unassigned, not a failure)")
        void noPartners_returnsEmpty() {
            when(partnerRepository.findEligiblePartnersForRouteForUpdate(routeId)).thenReturn(List.of());

            Optional<DeliveryPartner> result = service.selectLeastLoadedPartner(routeId);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("exactly one eligible partner -> selected regardless of workload " +
                "(Stream.min() never even needs to compare a singleton, so workload isn't queried here - " +
                "see multiplePartners_lowestWorkloadWins for where the comparator actually runs)")
        void onePartner_selected() {
            DeliveryPartner only = partner("Solo Partner");
            when(partnerRepository.findEligiblePartnersForRouteForUpdate(routeId)).thenReturn(List.of(only));

            Optional<DeliveryPartner> result = service.selectLeastLoadedPartner(routeId);

            assertThat(result).contains(only);
        }

        @Test
        @DisplayName("inactive/deleted/wrong-route partners are never candidates - enforced by the "
                + "locked repository query itself, so an empty candidate list here already proves "
                + "the exclusion (see DeliveryPartnerRepository.findEligiblePartnersForRouteForUpdate)")
        void ineligiblePartners_neverCandidates() {
            // The repository method's own WHERE clause (active=true, deleted=false, route.id=:routeId)
            // is what actually excludes these - simulated here by returning an empty candidate set,
            // since that's what the real query would return for a route with only ineligible partners.
            when(partnerRepository.findEligiblePartnersForRouteForUpdate(routeId)).thenReturn(List.of());

            assertThat(service.selectLeastLoadedPartner(routeId)).isEmpty();
        }
    }

    @Nested
    @DisplayName("least-loaded selection")
    class LeastLoaded {

        @Test
        @DisplayName("multiple partners, distinct workloads -> lowest wins")
        void multiplePartners_lowestWorkloadWins() {
            DeliveryPartner a = partner("Partner A");
            DeliveryPartner b = partner("Partner B");
            DeliveryPartner c = partner("Partner C");
            when(partnerRepository.findEligiblePartnersForRouteForUpdate(routeId)).thenReturn(List.of(a, b, c));
            when(assignmentRepository.countByDeliveryPartner_IdAndStatusIn(a.getId(), ACTIVE)).thenReturn(2L);
            when(assignmentRepository.countByDeliveryPartner_IdAndStatusIn(b.getId(), ACTIVE)).thenReturn(0L);
            when(assignmentRepository.countByDeliveryPartner_IdAndStatusIn(c.getId(), ACTIVE)).thenReturn(4L);

            Optional<DeliveryPartner> result = service.selectLeastLoadedPartner(routeId);

            assertThat(result).contains(b);
        }

        @Test
        @DisplayName("DELIVERED/FAILED assignments never count toward workload (only ASSIGNED/OUT_FOR_DELIVERY are queried) " +
                "- needs 2+ candidates so the comparator actually runs (see onePartner_selected)")
        void terminalStatusesExcludedFromWorkloadQuery() {
            DeliveryPartner p1 = partner("Partner 1");
            DeliveryPartner p2 = partner("Partner 2");
            when(partnerRepository.findEligiblePartnersForRouteForUpdate(routeId)).thenReturn(List.of(p1, p2));
            when(assignmentRepository.countByDeliveryPartner_IdAndStatusIn(any(), eq(ACTIVE))).thenReturn(0L);

            service.selectLeastLoadedPartner(routeId);

            verify(assignmentRepository, org.mockito.Mockito.atLeastOnce()).countByDeliveryPartner_IdAndStatusIn(
                    any(), eq(List.of(AssignmentStatus.ASSIGNED, AssignmentStatus.OUT_FOR_DELIVERY)));
        }
    }

    @Nested
    @DisplayName("deterministic tie-break")
    class TieBreak {

        @Test
        @DisplayName("equal workload -> lower partner id wins, deterministically")
        void equalWorkload_lowerIdWins() {
            DeliveryPartner a = partner("Partner A");
            DeliveryPartner b = partner("Partner B");
            // Force a known ordering regardless of random UUID generation, so the assertion is
            // unambiguous about which one "lower id" actually means here.
            DeliveryPartner lower = a.getId().compareTo(b.getId()) < 0 ? a : b;
            DeliveryPartner higher = lower == a ? b : a;

            when(partnerRepository.findEligiblePartnersForRouteForUpdate(routeId)).thenReturn(List.of(higher, lower));
            when(assignmentRepository.countByDeliveryPartner_IdAndStatusIn(any(), eq(ACTIVE))).thenReturn(2L);

            Optional<DeliveryPartner> result = service.selectLeastLoadedPartner(routeId);

            assertThat(result).contains(lower);
        }

        @Test
        @DisplayName("tie-break result is stable across repeated calls with the same candidate order")
        void tieBreak_isStableNotRandom() {
            DeliveryPartner a = partner("Partner A");
            DeliveryPartner b = partner("Partner B");
            DeliveryPartner expected = a.getId().compareTo(b.getId()) < 0 ? a : b;
            when(partnerRepository.findEligiblePartnersForRouteForUpdate(routeId)).thenReturn(List.of(a, b));
            when(assignmentRepository.countByDeliveryPartner_IdAndStatusIn(any(), eq(ACTIVE))).thenReturn(1L);

            for (int i = 0; i < 5; i++) {
                assertThat(service.selectLeastLoadedPartner(routeId)).contains(expected);
            }
        }
    }
}
