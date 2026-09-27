package com.farm2home.delivery.integration;

import com.farm2home.core.test.BaseIntegrationTest;
import com.farm2home.core.test.AuthenticationTestBuilder;
import com.farm2home.delivery.client.OrderDetailResponse;
import com.farm2home.delivery.client.OrderServiceClient;
import com.farm2home.delivery.client.PaymentServiceClient;
import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.entity.DeliveryRoute;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import com.farm2home.delivery.domain.repository.DeliveryRouteRepository;
import com.farm2home.delivery.dto.request.CreatePartnerRequest;
import com.farm2home.delivery.dto.request.ManualAssignRequest;
import com.farm2home.delivery.dto.request.UpdateAssignmentStatusRequest;
import com.farm2home.delivery.dto.request.UpdatePartnerRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Delivery Service using Testcontainers and PostgreSQL.
 * Tests delivery partner management and assignment workflows.
 *
 * <p>Only the two cross-service HTTP clients are mocked - manualAssign() resolves the order from
 * order-service, and dispatching to OUT_FOR_DELIVERY checks payment-service for an initiated
 * payment. Both are separate deployables that don't exist in this test's context.
 */
@DisplayName("Delivery Service Integration Tests")
class DeliveryServiceIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DeliveryPartnerRepository partnerRepository;

    @Autowired
    private DeliveryAssignmentRepository assignmentRepository;

    @Autowired
    private DeliveryRouteRepository routeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderServiceClient orderServiceClient;

    @MockBean
    private PaymentServiceClient paymentServiceClient;

    private UUID orderId;
    private DeliveryRoute route;
    private AuthenticationTestBuilder authBuilder;

    @BeforeEach
    void setUp() {
        orderId = UUID.randomUUID();
        authBuilder = new AuthenticationTestBuilder()
                .withMobile("9876500000")
                .withRoles("FARM_MANAGER");
        // Children before parents: assignments reference partners and routes, partners reference routes.
        assignmentRepository.deleteAll();
        partnerRepository.deleteAll();
        routeRepository.deleteAll();

        route = createTestRoute();

        var order = new OrderDetailResponse();
        order.setId(orderId);
        order.setOrderNumber("ORD-TEST-000001");
        order.setCustomerId(UUID.randomUUID());
        order.setStatus("PENDING");
        order.setDeliveryRouteId(route.getId());
        when(orderServiceClient.getOrder(any())).thenReturn(Mono.just(order));
        when(paymentServiceClient.hasPayableProgress(any())).thenReturn(Mono.just(true));
    }

    @Nested
    @DisplayName("Delivery Partner Management")
    class DeliveryPartnerManagementTests {

        @Test
        @DisplayName("Should register a new delivery partner")
        void shouldRegisterNewDeliveryPartner() throws Exception {
            var partnerId = UUID.randomUUID();
            var partnerRequest = new CreatePartnerRequest();
            partnerRequest.setUserId(partnerId);
            partnerRequest.setName("John Delivery");
            partnerRequest.setMobile("9876543210");
            partnerRequest.setVehicleType("2-Wheeler");

            mockMvc.perform(post("/api/v1/delivery/partners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(partnerRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id", notNullValue()))
                    .andExpect(jsonPath("$.data.name").value("John Delivery"))
                    .andExpect(jsonPath("$.data.mobile").value("9876543210"))
                    .andExpect(jsonPath("$.data.active").value(true));

            var savedPartners = partnerRepository.findAll();
            assertThat(savedPartners).hasSize(1);
            assertThat(savedPartners.get(0).getName()).isEqualTo("John Delivery");
        }

        @Test
        @DisplayName("Should retrieve all delivery partners")
        void shouldRetrieveAllDeliveryPartners() throws Exception {
            createTestPartner("Partner 1");
            createTestPartner("Partner 2");
            createTestPartner("Partner 3");

            mockMvc.perform(get("/api/v1/delivery/partners")
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(3)))
                    .andExpect(jsonPath("$.data.totalElements").value(3));
        }

        @Test
        @DisplayName("Should include inactive partners in the list, flagged active=false")
        void shouldFlagInactiveDeliveryPartners() throws Exception {
            // The partner list is documented as NOT filtered by the active flag (there is no
            // active-only endpoint) - inactive partners are returned with active=false.
            createTestPartner("Active Partner");
            var inactivePartner = createTestPartner("Inactive Partner");
            inactivePartner.setActive(false);
            partnerRepository.save(inactivePartner);

            mockMvc.perform(get("/api/v1/delivery/partners")
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)))
                    .andExpect(jsonPath("$.data.content[?(@.name == 'Active Partner')].active", contains(true)))
                    .andExpect(jsonPath("$.data.content[?(@.name == 'Inactive Partner')].active", contains(false)));
        }

        @Test
        @DisplayName("Should update delivery partner details")
        void shouldUpdateDeliveryPartnerDetails() throws Exception {
            var partner = createTestPartner("Original Name");

            var updateRequest = new UpdatePartnerRequest();
            updateRequest.setName("Updated Name");
            updateRequest.setMobile("9876543210");
            updateRequest.setVehicleType("3-Wheeler");

            mockMvc.perform(put("/api/v1/delivery/partners/" + partner.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.name").value("Updated Name"))
                    .andExpect(jsonPath("$.data.vehicleType").value("3-Wheeler"));
        }
    }

    @Nested
    @DisplayName("Delivery Assignment")
    class DeliveryAssignmentTests {

        @Test
        @DisplayName("Should create delivery assignment for order")
        void shouldCreateDeliveryAssignment() throws Exception {
            var partner = createTestPartner("Delivery Partner");

            var assignmentRequest = new ManualAssignRequest();
            assignmentRequest.setOrderId(orderId);
            assignmentRequest.setDeliveryPartnerId(partner.getId());

            mockMvc.perform(post("/api/v1/delivery/assignments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(assignmentRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.id", notNullValue()))
                    .andExpect(jsonPath("$.data.status").value(AssignmentStatus.ASSIGNED.name()))
                    // No override in the request, so the order's own route is used
                    .andExpect(jsonPath("$.data.routeId").value(route.getId().toString()));

            var savedAssignments = assignmentRepository.findAll();
            assertThat(savedAssignments).hasSize(1);
        }

        @Test
        @DisplayName("Should update assignment status to OUT_FOR_DELIVERY")
        void shouldUpdateAssignmentStatusToOutForDelivery() throws Exception {
            var assignment = createTestAssignment();

            var updateRequest = new UpdateAssignmentStatusRequest();
            updateRequest.setStatus(AssignmentStatus.OUT_FOR_DELIVERY);

            mockMvc.perform(patch("/api/v1/delivery/assignments/" + assignment.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(AssignmentStatus.OUT_FOR_DELIVERY.name()));

            var updatedAssignment = assignmentRepository.findById(assignment.getId()).orElseThrow();
            assertThat(updatedAssignment.getStatus()).isEqualTo(AssignmentStatus.OUT_FOR_DELIVERY);
        }

        @Test
        @DisplayName("Should update assignment status to DELIVERED")
        void shouldUpdateAssignmentStatusToDelivered() throws Exception {
            var assignment = createTestAssignment();
            assignment.setStatus(AssignmentStatus.OUT_FOR_DELIVERY);
            assignmentRepository.save(assignment);

            var updateRequest = new UpdateAssignmentStatusRequest();
            updateRequest.setStatus(AssignmentStatus.DELIVERED);
            updateRequest.setDeliveryProof("Delivery photo URL");

            mockMvc.perform(patch("/api/v1/delivery/assignments/" + assignment.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(AssignmentStatus.DELIVERED.name()));
        }

        @Test
        @DisplayName("Should retrieve only the calling delivery partner's own assignments")
        void shouldRetrieveAssignmentsByPartner() throws Exception {
            var partner1 = createTestPartner("Partner 1");
            var partner2 = createTestPartner("Partner 2");

            createTestAssignmentForPartner(partner1);
            createTestAssignmentForPartner(partner1);
            createTestAssignmentForPartner(partner2);

            // A non-admin caller's assignment list is scoped to their own DeliveryPartner profile
            var partner1Auth = new AuthenticationTestBuilder()
                    .withUserId(partner1.getUserId())
                    .withMobile(partner1.getMobile())
                    .withRoles("DELIVERY_PARTNER");

            mockMvc.perform(get("/api/v1/delivery/assignments")
                    .with(partner1Auth.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.content", hasSize(2)));
        }

        @Test
        @DisplayName("Should mark assignment as failed")
        void shouldMarkAssignmentAsFailed() throws Exception {
            var assignment = createTestAssignment();

            var updateRequest = new UpdateAssignmentStatusRequest();
            updateRequest.setStatus(AssignmentStatus.FAILED);
            updateRequest.setFailureReason("Customer not available");

            mockMvc.perform(patch("/api/v1/delivery/assignments/" + assignment.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value(AssignmentStatus.FAILED.name()));

            var updatedAssignment = assignmentRepository.findById(assignment.getId()).orElseThrow();
            assertThat(updatedAssignment.getStatus()).isEqualTo(AssignmentStatus.FAILED);
            assertThat(updatedAssignment.getFailureReason()).isEqualTo("Customer not available");
        }
    }

    // Helper methods
    private DeliveryRoute createTestRoute() {
        var route = DeliveryRoute.builder()
                .routeName("Test Route")
                .routeCode("RT-" + UUID.randomUUID().toString().substring(0, 8))
                .area("Test Area")
                .city("Test City")
                .pincode("500001")
                .build();
        return routeRepository.save(route);
    }

    private DeliveryPartner createTestPartner(String name) {
        var partner = DeliveryPartner.builder()
                .userId(UUID.randomUUID())
                .route(route)
                .name(name)
                .mobile(String.valueOf(ThreadLocalRandom.current().nextLong(6_000_000_000L, 10_000_000_000L)))
                .vehicleType("2-Wheeler")
                .active(true)
                .build();
        return partnerRepository.save(partner);
    }

    private DeliveryAssignment createTestAssignment() {
        var partner = createTestPartner("Test Partner");
        return createTestAssignmentForPartner(partner);
    }

    private DeliveryAssignment createTestAssignmentForPartner(DeliveryPartner partner) {
        var assignment = DeliveryAssignment.builder()
                .orderId(UUID.randomUUID())
                .deliveryPartner(partner)
                .route(partner.getRoute())
                .status(AssignmentStatus.ASSIGNED)
                .build();
        return assignmentRepository.save(assignment);
    }
}
