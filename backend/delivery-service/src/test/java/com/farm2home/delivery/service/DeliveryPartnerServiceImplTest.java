package com.farm2home.delivery.service;

import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import com.farm2home.delivery.domain.repository.DeliveryRouteRepository;
import com.farm2home.delivery.dto.request.CreatePartnerRequest;
import com.farm2home.delivery.dto.request.UpdatePartnerRequest;
import com.farm2home.delivery.dto.response.PartnerResponse;
import com.farm2home.delivery.exception.ResourceNotFoundException;
import com.farm2home.delivery.mapper.DeliveryMapper;
import com.farm2home.delivery.service.impl.DeliveryPartnerServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DeliveryPartnerServiceImplTest {

    @Mock private DeliveryPartnerRepository partnerRepository;
    @Mock private DeliveryRouteRepository routeRepository;
    @Mock private DeliveryAssignmentRepository assignmentRepository;
    @Mock private DeliveryMapper mapper;

    @InjectMocks private DeliveryPartnerServiceImpl service;

    // activeDeliveries is computed live (DeliveryPartnerServiceImpl.toResponse) on every response
    // this service builds - unstubbed by default here (0), since none of these tests care about
    // its exact value; PartnerSelectionServiceImplTest covers the workload logic itself.
    @BeforeEach
    void stubWorkloadDefault() {
        lenient().when(assignmentRepository.countByDeliveryPartner_IdAndStatusIn(any(), any())).thenReturn(0L);
    }

    private final UUID partnerId = UUID.randomUUID();
    private final UUID routeId = UUID.randomUUID();

    private DeliveryPartner buildPartner() {
        return DeliveryPartner.builder().id(partnerId).userId(UUID.randomUUID())
                .name("Raj Kumar").mobile("9876543210").active(true).deleted(false).build();
    }

    private DeliveryRoute buildRoute() {
        return DeliveryRoute.builder().id(routeId).routeCode("NZ1").build();
    }

    private PartnerResponse buildResponse() {
        return PartnerResponse.builder().id(partnerId).name("Raj Kumar").build();
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("no routeId → creates partner unassigned to any route")
        void noRoute_createsUnassigned() {
            CreatePartnerRequest req = new CreatePartnerRequest();
            req.setUserId(UUID.randomUUID());
            req.setName("Raj Kumar");
            req.setMobile("9876543210");

            when(mapper.toEntity(req)).thenReturn(new DeliveryPartner());
            DeliveryPartner saved = buildPartner();
            when(partnerRepository.save(any())).thenReturn(saved);
            when(mapper.toPartnerResponse(saved)).thenReturn(buildResponse());

            PartnerResponse result = service.create(req);

            assertThat(result.getName()).isEqualTo("Raj Kumar");
            verifyNoInteractions(routeRepository);
        }

        @Test
        @DisplayName("with routeId → looks up route and assigns it")
        void withRoute_assignsRoute() {
            CreatePartnerRequest req = new CreatePartnerRequest();
            req.setRouteId(routeId);
            req.setUserId(UUID.randomUUID());
            req.setName("Raj Kumar");
            req.setMobile("9876543210");

            DeliveryRoute route = buildRoute();
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.of(route));
            DeliveryPartner entity = new DeliveryPartner();
            when(mapper.toEntity(req)).thenReturn(entity);
            when(partnerRepository.save(any())).thenReturn(buildPartner());
            when(mapper.toPartnerResponse(any())).thenReturn(buildResponse());

            service.create(req);

            assertThat(entity.getRoute()).isEqualTo(route);
        }

        @Test
        @DisplayName("routeId not found → throws ResourceNotFoundException")
        void routeNotFound_throws() {
            CreatePartnerRequest req = new CreatePartnerRequest();
            req.setRouteId(routeId);
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(ResourceNotFoundException.class);
            verify(partnerRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("returns mapped page")
        void returnsMappedPage() {
            DeliveryPartner partner = buildPartner();
            when(partnerRepository.findAllByDeletedFalse(any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(partner)));
            when(mapper.toPartnerResponse(partner)).thenReturn(buildResponse());

            assertThat(service.findAll(org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("existing partner → returns response")
        void found() {
            DeliveryPartner partner = buildPartner();
            when(partnerRepository.findByIdAndDeletedFalse(partnerId)).thenReturn(Optional.of(partner));
            when(mapper.toPartnerResponse(partner)).thenReturn(buildResponse());

            PartnerResponse result = service.findById(partnerId);
            assertThat(result.getId()).isEqualTo(partnerId);
        }

        @Test
        @DisplayName("missing partner → throws ResourceNotFoundException")
        void notFound() {
            when(partnerRepository.findByIdAndDeletedFalse(partnerId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(partnerId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("with new routeId → reassigns route and applies other fields")
        void reassignsRoute() {
            DeliveryPartner partner = buildPartner();
            UpdatePartnerRequest req = new UpdatePartnerRequest();
            req.setRouteId(routeId);
            req.setName("Raj K.");

            DeliveryRoute route = buildRoute();
            when(partnerRepository.findByIdAndDeletedFalse(partnerId)).thenReturn(Optional.of(partner));
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.of(route));
            doAnswer(inv -> {
                partner.setName(req.getName());
                return null;
            }).when(mapper).updatePartnerFromRequest(req, partner);
            when(partnerRepository.save(partner)).thenReturn(partner);
            when(mapper.toPartnerResponse(partner)).thenReturn(buildResponse());

            service.update(partnerId, req);

            assertThat(partner.getRoute()).isEqualTo(route);
            assertThat(partner.getName()).isEqualTo("Raj K.");
        }

        @Test
        @DisplayName("routeId in request not found → throws ResourceNotFoundException")
        void routeNotFound_throws() {
            DeliveryPartner partner = buildPartner();
            UpdatePartnerRequest req = new UpdatePartnerRequest();
            req.setRouteId(routeId);
            when(partnerRepository.findByIdAndDeletedFalse(partnerId)).thenReturn(Optional.of(partner));
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.update(partnerId, req))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
