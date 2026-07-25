package com.farm2home.delivery.service;

import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.domain.repository.DeliveryRouteRepository;
import com.farm2home.delivery.dto.request.CreateRouteRequest;
import com.farm2home.delivery.dto.request.UpdateRouteRequest;
import com.farm2home.delivery.dto.response.RouteResponse;
import com.farm2home.delivery.exception.DeliveryException;
import com.farm2home.delivery.exception.ResourceNotFoundException;
import com.farm2home.delivery.mapper.DeliveryMapper;
import com.farm2home.delivery.service.impl.DeliveryRouteServiceImpl;
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
class DeliveryRouteServiceImplTest {

    @Mock private DeliveryRouteRepository routeRepository;
    @Mock private DeliveryMapper mapper;

    @InjectMocks private DeliveryRouteServiceImpl service;

    private final UUID routeId = UUID.randomUUID();

    private DeliveryRoute buildRoute() {
        return DeliveryRoute.builder().id(routeId).routeName("North Zone").routeCode("NZ1")
                .area("North").city("Metropolis").pincode("560001").active(true).deleted(false).build();
    }

    private RouteResponse buildResponse() {
        return RouteResponse.builder().id(routeId).routeCode("NZ1").build();
    }

    @Nested
    @DisplayName("create()")
    class Create {

        @Test
        @DisplayName("new route code → saves and returns response")
        void happyPath() {
            CreateRouteRequest req = new CreateRouteRequest();
            req.setRouteName("North Zone");
            req.setRouteCode("nz1");
            req.setArea("North");
            req.setCity("Metropolis");
            req.setPincode("560001");

            when(routeRepository.existsByRouteCodeAndDeletedFalse("nz1")).thenReturn(false);
            when(mapper.toEntity(req)).thenReturn(new DeliveryRoute());
            DeliveryRoute saved = buildRoute();
            when(routeRepository.save(any())).thenReturn(saved);
            when(mapper.toRouteResponse(saved)).thenReturn(buildResponse());

            RouteResponse result = service.create(req);

            assertThat(result.getRouteCode()).isEqualTo("NZ1");
            verify(routeRepository).save(any(DeliveryRoute.class));
        }

        @Test
        @DisplayName("duplicate route code → throws DeliveryException")
        void duplicateCode_throws() {
            CreateRouteRequest req = new CreateRouteRequest();
            req.setRouteCode("NZ1");
            when(routeRepository.existsByRouteCodeAndDeletedFalse("NZ1")).thenReturn(true);

            assertThatThrownBy(() -> service.create(req))
                    .isInstanceOf(DeliveryException.class)
                    .hasMessageContaining("already exists");
            verify(routeRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("findAll()")
    class FindAll {

        @Test
        @DisplayName("returns mapped page")
        void returnsMappedPage() {
            DeliveryRoute route = buildRoute();
            when(routeRepository.findAllByDeletedFalse(any())).thenReturn(
                    new org.springframework.data.domain.PageImpl<>(java.util.List.of(route)));
            when(mapper.toRouteResponse(route)).thenReturn(buildResponse());

            assertThat(service.findAll(org.springframework.data.domain.Pageable.unpaged()).getTotalElements())
                    .isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("existing route → returns response")
        void found() {
            DeliveryRoute route = buildRoute();
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.of(route));
            when(mapper.toRouteResponse(route)).thenReturn(buildResponse());

            RouteResponse result = service.findById(routeId);
            assertThat(result.getId()).isEqualTo(routeId);
        }

        @Test
        @DisplayName("missing route → throws ResourceNotFoundException")
        void notFound() {
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.findById(routeId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }

    @Nested
    @DisplayName("update()")
    class Update {

        @Test
        @DisplayName("partial update → mapper applies non-null fields")
        void partialUpdate() {
            DeliveryRoute route = buildRoute();
            UpdateRouteRequest req = new UpdateRouteRequest();
            req.setArea("Updated Area");

            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.of(route));
            doAnswer(inv -> {
                route.setArea(req.getArea());
                return null;
            }).when(mapper).updateRouteFromRequest(req, route);
            when(routeRepository.save(route)).thenReturn(route);
            when(mapper.toRouteResponse(route)).thenReturn(buildResponse());

            service.update(routeId, req);

            assertThat(route.getArea()).isEqualTo("Updated Area");
            verify(routeRepository).save(route);
        }
    }

    @Nested
    @DisplayName("delete()")
    class Delete {

        @Test
        @DisplayName("existing route → sets deleted=true")
        void softDeletes() {
            DeliveryRoute route = buildRoute();
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.of(route));

            service.delete(routeId);

            assertThat(route.isDeleted()).isTrue();
            verify(routeRepository).save(route);
        }

        @Test
        @DisplayName("missing route → throws ResourceNotFoundException")
        void notFound_throws() {
            when(routeRepository.findByIdAndDeletedFalse(routeId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.delete(routeId))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
    }
}
