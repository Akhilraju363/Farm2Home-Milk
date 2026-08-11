package com.farm2home.delivery.service;

import com.farm2home.delivery.config.UserPrincipal;
import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryLocation;
import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryLocationRepository;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import com.farm2home.delivery.dto.request.SubmitLocationRequest;
import com.farm2home.delivery.dto.response.LocationResponse;
import com.farm2home.delivery.exception.DeliveryException;
import com.farm2home.delivery.exception.ResourceNotFoundException;
import com.farm2home.delivery.service.impl.DeliveryLocationServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryLocationServiceImplTest {

    @Mock private DeliveryLocationRepository locationRepository;
    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @Mock private DeliveryPartnerRepository partnerRepository;

    @InjectMocks private DeliveryLocationServiceImpl service;

    private final UUID assignmentId = UUID.randomUUID();
    private final UUID partnerUserId = UUID.randomUUID();
    private final UUID partnerId = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();

    private DeliveryPartner buildPartner() {
        return DeliveryPartner.builder().id(partnerId).userId(partnerUserId).name("Ravi").mobile("9876543210").build();
    }

    private DeliveryAssignment buildAssignment(AssignmentStatus status) {
        return DeliveryAssignment.builder()
                .id(assignmentId).orderId(UUID.randomUUID()).customerId(customerId)
                .deliveryPartner(buildPartner()).status(status).build();
    }

    private SubmitLocationRequest buildRequest(double lat, double lng) {
        SubmitLocationRequest req = new SubmitLocationRequest();
        req.setLatitude(lat);
        req.setLongitude(lng);
        return req;
    }

    @Nested
    @DisplayName("submitLocation()")
    class SubmitLocation {

        @Test
        @DisplayName("owning partner, OUT_FOR_DELIVERY → persists with server-assigned recordedAt")
        void ownerSubmits_outForDelivery_ok() {
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerUserId)).thenReturn(Optional.of(buildPartner()));
            when(assignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partnerId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY)));
            when(locationRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            LocationResponse response = service.submitLocation(assignmentId, buildRequest(17.4123, 78.4482), partnerUserId);

            assertThat(response.getLatitude()).isEqualTo(17.4123);
            assertThat(response.getLongitude()).isEqualTo(78.4482);
            assertThat(response.getFreshness()).isEqualTo("LIVE");

            ArgumentCaptor<DeliveryLocation> captor = ArgumentCaptor.forClass(DeliveryLocation.class);
            verify(locationRepository).save(captor.capture());
            assertThat(captor.getValue().getRecordedAt()).isCloseTo(LocalDateTime.now(), within3Seconds());
            assertThat(captor.getValue().getDeliveryPartnerId()).isEqualTo(partnerId);
        }

        @Test
        @DisplayName("assignment not OUT_FOR_DELIVERY (still ASSIGNED) → rejected")
        void notYetOutForDelivery_rejected() {
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerUserId)).thenReturn(Optional.of(buildPartner()));
            when(assignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partnerId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.ASSIGNED)));

            assertThatThrownBy(() -> service.submitLocation(assignmentId, buildRequest(17.0, 78.0), partnerUserId))
                    .isInstanceOf(DeliveryException.class)
                    .hasMessageContaining("OUT_FOR_DELIVERY");
            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("assignment already DELIVERED → rejected, tracking has ended")
        void alreadyDelivered_rejected() {
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerUserId)).thenReturn(Optional.of(buildPartner()));
            when(assignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partnerId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.DELIVERED)));

            assertThatThrownBy(() -> service.submitLocation(assignmentId, buildRequest(17.0, 78.0), partnerUserId))
                    .isInstanceOf(DeliveryException.class);
            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("Partner B submits for Partner A's assignment → 404, not found")
        void wrongPartner_notFound() {
            UUID partnerBUserId = UUID.randomUUID();
            UUID partnerBId = UUID.randomUUID();
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerBUserId))
                    .thenReturn(Optional.of(DeliveryPartner.builder().id(partnerBId).userId(partnerBUserId).build()));
            when(assignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partnerBId))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitLocation(assignmentId, buildRequest(17.0, 78.0), partnerBUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("caller has no delivery partner profile at all → 404")
        void noPartnerProfile_notFound() {
            UUID randomUserId = UUID.randomUUID();
            when(partnerRepository.findByUserIdAndDeletedFalse(randomUserId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.submitLocation(assignmentId, buildRequest(17.0, 78.0), randomUserId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("latitude out of range → rejected, never persisted")
        void invalidLatitude_rejected() {
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerUserId)).thenReturn(Optional.of(buildPartner()));
            when(assignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partnerId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY)));

            assertThatThrownBy(() -> service.submitLocation(assignmentId, buildRequest(95.0, 78.0), partnerUserId))
                    .isInstanceOf(DeliveryException.class)
                    .hasMessageContaining("latitude");
            verify(locationRepository, never()).save(any());
        }

        @Test
        @DisplayName("NaN longitude → rejected as not a finite number")
        void nanLongitude_rejected() {
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerUserId)).thenReturn(Optional.of(buildPartner()));
            when(assignmentRepository.findByIdAndDeliveryPartnerId(assignmentId, partnerId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY)));

            assertThatThrownBy(() -> service.submitLocation(assignmentId, buildRequest(17.0, Double.NaN), partnerUserId))
                    .isInstanceOf(DeliveryException.class)
                    .hasMessageContaining("finite");
            verify(locationRepository, never()).save(any());
        }

        private org.assertj.core.data.TemporalUnitOffset within3Seconds() {
            return org.assertj.core.api.Assertions.within(3, java.time.temporal.ChronoUnit.SECONDS);
        }
    }

    @Nested
    @DisplayName("getCurrentLocation() - ownership")
    class GetCurrentLocation {

        @Test
        @DisplayName("admin → allowed for any assignment")
        void admin_allowed() {
            UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), "9876543210", Set.of("SUPER_ADMIN"));
            DeliveryAssignment assignment = buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY);
            when(assignmentRepository.findById(assignmentId)).thenReturn(Optional.of(assignment));
            DeliveryLocation loc = DeliveryLocation.builder().deliveryAssignmentId(assignmentId)
                    .latitude(17.0).longitude(78.0).recordedAt(LocalDateTime.now()).build();
            when(locationRepository.findTopByDeliveryAssignmentIdOrderByRecordedAtDesc(assignmentId))
                    .thenReturn(Optional.of(loc));

            Optional<LocationResponse> result = service.getCurrentLocation(assignmentId, admin);

            assertThat(result).isPresent();
        }

        @Test
        @DisplayName("owning delivery partner → allowed")
        void owningPartner_allowed() {
            UserPrincipal partner = new UserPrincipal(partnerUserId, "9876543210", Set.of("DELIVERY_PARTNER"));
            when(assignmentRepository.findById(assignmentId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY)));
            when(partnerRepository.findByUserIdAndDeletedFalse(partnerUserId)).thenReturn(Optional.of(buildPartner()));
            when(locationRepository.findTopByDeliveryAssignmentIdOrderByRecordedAtDesc(assignmentId)).thenReturn(Optional.empty());

            Optional<LocationResponse> result = service.getCurrentLocation(assignmentId, partner);

            assertThat(result).isEmpty(); // no location submitted yet, but no exception thrown = allowed
        }

        @Test
        @DisplayName("a different delivery partner → 404, not found")
        void otherPartner_denied() {
            UUID otherPartnerUserId = UUID.randomUUID();
            UUID otherPartnerId = UUID.randomUUID();
            UserPrincipal otherPartner = new UserPrincipal(otherPartnerUserId, "9876543211", Set.of("DELIVERY_PARTNER"));
            when(assignmentRepository.findById(assignmentId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY)));
            when(partnerRepository.findByUserIdAndDeletedFalse(otherPartnerUserId))
                    .thenReturn(Optional.of(DeliveryPartner.builder().id(otherPartnerId).userId(otherPartnerUserId).build()));

            assertThatThrownBy(() -> service.getCurrentLocation(assignmentId, otherPartner))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("the order's own customer → allowed")
        void owningCustomer_allowed() {
            UserPrincipal customer = new UserPrincipal(customerId, "9876543212", Set.of("CUSTOMER"));
            when(assignmentRepository.findById(assignmentId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY)));
            when(locationRepository.findTopByDeliveryAssignmentIdOrderByRecordedAtDesc(assignmentId)).thenReturn(Optional.empty());

            assertThat(service.getCurrentLocation(assignmentId, customer)).isEmpty(); // allowed, just no data yet
        }

        @Test
        @DisplayName("a different customer → 404, not found (IDOR denied)")
        void otherCustomer_denied() {
            UserPrincipal otherCustomer = new UserPrincipal(UUID.randomUUID(), "9876543213", Set.of("CUSTOMER"));
            when(assignmentRepository.findById(assignmentId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY)));

            assertThatThrownBy(() -> service.getCurrentLocation(assignmentId, otherCustomer))
                    .isInstanceOf(ResourceNotFoundException.class);
        }

        @Test
        @DisplayName("assignment DELIVERED → returns empty, tracking has ended, never queries location")
        void delivered_returnsEmptyWithoutQuerying() {
            UserPrincipal customer = new UserPrincipal(customerId, "9876543212", Set.of("CUSTOMER"));
            when(assignmentRepository.findById(assignmentId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.DELIVERED)));

            Optional<LocationResponse> result = service.getCurrentLocation(assignmentId, customer);

            assertThat(result).isEmpty();
            verify(locationRepository, never()).findTopByDeliveryAssignmentIdOrderByRecordedAtDesc(any());
        }

        @Test
        @DisplayName("recent-but-not-live reading → freshness RECENT")
        void staleness_recent() {
            UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), "9876543210", Set.of("SUPER_ADMIN"));
            when(assignmentRepository.findById(assignmentId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY)));
            DeliveryLocation loc = DeliveryLocation.builder().deliveryAssignmentId(assignmentId)
                    .latitude(17.0).longitude(78.0).recordedAt(LocalDateTime.now().minusMinutes(3)).build();
            when(locationRepository.findTopByDeliveryAssignmentIdOrderByRecordedAtDesc(assignmentId))
                    .thenReturn(Optional.of(loc));

            LocationResponse response = service.getCurrentLocation(assignmentId, admin).orElseThrow();

            assertThat(response.getFreshness()).isEqualTo("RECENT");
        }

        @Test
        @DisplayName("old reading (>5 min) → freshness STALE")
        void staleness_stale() {
            UserPrincipal admin = new UserPrincipal(UUID.randomUUID(), "9876543210", Set.of("SUPER_ADMIN"));
            when(assignmentRepository.findById(assignmentId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY)));
            DeliveryLocation loc = DeliveryLocation.builder().deliveryAssignmentId(assignmentId)
                    .latitude(17.0).longitude(78.0).recordedAt(LocalDateTime.now().minusMinutes(10)).build();
            when(locationRepository.findTopByDeliveryAssignmentIdOrderByRecordedAtDesc(assignmentId))
                    .thenReturn(Optional.of(loc));

            LocationResponse response = service.getCurrentLocation(assignmentId, admin).orElseThrow();

            assertThat(response.getFreshness()).isEqualTo("STALE");
        }

        @Test
        @DisplayName("unrelated role with no match → 404")
        void unrelatedRole_denied() {
            UserPrincipal deliveryPartnerWithNoProfile = new UserPrincipal(UUID.randomUUID(), "9876543214", Set.of("DELIVERY_PARTNER"));
            when(assignmentRepository.findById(assignmentId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY)));
            when(partnerRepository.findByUserIdAndDeletedFalse(deliveryPartnerWithNoProfile.userId()))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.getCurrentLocation(assignmentId, deliveryPartnerWithNoProfile))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("getHistory()")
    class GetHistory {

        @Test
        @DisplayName("owning customer → returns paginated history, most recent first")
        void ownerGetsHistory() {
            UserPrincipal customer = new UserPrincipal(customerId, "9876543212", Set.of("CUSTOMER"));
            when(assignmentRepository.findById(assignmentId))
                    .thenReturn(Optional.of(buildAssignment(AssignmentStatus.OUT_FOR_DELIVERY)));
            DeliveryLocation loc = DeliveryLocation.builder().deliveryAssignmentId(assignmentId)
                    .latitude(17.0).longitude(78.0).recordedAt(LocalDateTime.now()).build();
            when(locationRepository.findAllByDeliveryAssignmentIdOrderByRecordedAtDesc(eq(assignmentId), any()))
                    .thenReturn(new PageImpl<>(List.of(loc), PageRequest.of(0, 20), 1));

            Page<LocationResponse> result = service.getHistory(assignmentId, customer, PageRequest.of(0, 20));

            assertThat(result.getContent()).hasSize(1);
        }

        private UUID eq(UUID id) {
            return org.mockito.ArgumentMatchers.eq(id);
        }
    }
}
