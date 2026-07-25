package com.farm2home.delivery.integration;

import com.farm2home.core.test.BaseIntegrationTest;
import com.farm2home.core.test.AuthenticationTestBuilder;
import com.farm2home.delivery.domain.entity.DeliveryAssignment;
import com.farm2home.delivery.domain.entity.DeliveryPartner;
import com.farm2home.delivery.domain.enums.AssignmentStatus;
import com.farm2home.delivery.domain.repository.DeliveryAssignmentRepository;
import com.farm2home.delivery.domain.repository.DeliveryPartnerRepository;
import com.farm2home.delivery.dto.request.CreatePartnerRequest;
import com.farm2home.delivery.dto.request.UpdateAssignmentStatusRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for the Delivery Service using Testcontainers and PostgreSQL.
 * Tests delivery partner management, route creation, and assignment workflows.
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
    private ObjectMapper objectMapper;

    private UUID customerId;
    private UUID orderId;
    private AuthenticationTestBuilder authBuilder;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        authBuilder = new AuthenticationTestBuilder()
                .withUserId(customerId)
                .withUsername("admin@farm2home.com")
                .withRoles("ADMIN");
        partnerRepository.deleteAll();
        assignmentRepository.deleteAll();
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
            partnerRequest.setMobile("+919876543210");
            partnerRequest.setVehicleType("2-Wheeler");

            mockMvc.perform(post("/api/v1/delivery-partners")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(partnerRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.name").value("John Delivery"))
                    .andExpect(jsonPath("$.mobile").value("+919876543210"))
                    .andExpect(jsonPath("$.active").value(true));

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

            mockMvc.perform(get("/api/v1/delivery-partners")
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(3)));
        }

        @Test
        @DisplayName("Should retrieve active delivery partners only")
        void shouldRetrieveActiveDeliveryPartners() throws Exception {
            createTestPartner("Active Partner");
            var inactivePartner = createTestPartner("Inactive Partner");
            inactivePartner.setActive(false);
            partnerRepository.save(inactivePartner);

            mockMvc.perform(get("/api/v1/delivery-partners/active")
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)));
        }

        @Test
        @DisplayName("Should update delivery partner details")
        void shouldUpdateDeliveryPartnerDetails() throws Exception {
            var partner = createTestPartner("Original Name");

            var updateRequest = new CreatePartnerRequest();
            updateRequest.setUserId(partner.getUserId());
            updateRequest.setName("Updated Name");
            updateRequest.setMobile("+919876543210");
            updateRequest.setVehicleType("3-Wheeler");

            mockMvc.perform(put("/api/v1/delivery-partners/" + partner.getId())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.name").value("Updated Name"));
        }
    }

    @Nested
    @DisplayName("Delivery Assignment")
    class DeliveryAssignmentTests {

        @Test
        @DisplayName("Should create delivery assignment for order")
        void shouldCreateDeliveryAssignment() throws Exception {
            var partner = createTestPartner("Delivery Partner");
            var currentOrderId = orderId;

            var assignmentRequest = new Object() {
                public String orderId = currentOrderId.toString();
                public String deliveryPartnerId = partner.getId().toString();
                public String address = "123 Main St, City";
            };

            mockMvc.perform(post("/api/v1/delivery-assignments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(assignmentRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id", notNullValue()))
                    .andExpect(jsonPath("$.status").value(AssignmentStatus.ASSIGNED.name()));

            var savedAssignments = assignmentRepository.findAll();
            assertThat(savedAssignments).hasSize(1);
        }

        @Test
        @DisplayName("Should update assignment status to OUT_FOR_DELIVERY")
        void shouldUpdateAssignmentStatusToOutForDelivery() throws Exception {
            var assignment = createTestAssignment();

            var updateRequest = new UpdateAssignmentStatusRequest();
            updateRequest.setStatus(AssignmentStatus.OUT_FOR_DELIVERY);

            mockMvc.perform(patch("/api/v1/delivery-assignments/" + assignment.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(AssignmentStatus.OUT_FOR_DELIVERY.name()));

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

            mockMvc.perform(patch("/api/v1/delivery-assignments/" + assignment.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(AssignmentStatus.DELIVERED.name()));
        }

        @Test
        @DisplayName("Should retrieve assignments by delivery partner")
        void shouldRetrieveAssignmentsByPartner() throws Exception {
            var partner1 = createTestPartner("Partner 1");
            var partner2 = createTestPartner("Partner 2");

            createTestAssignmentForPartner(partner1);
            createTestAssignmentForPartner(partner1);
            createTestAssignmentForPartner(partner2);

            mockMvc.perform(get("/api/v1/delivery-partners/" + partner1.getId() + "/assignments")
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(2)));
        }

        @Test
        @DisplayName("Should mark assignment as failed")
        void shouldMarkAssignmentAsFailed() throws Exception {
            var assignment = createTestAssignment();

            var updateRequest = new UpdateAssignmentStatusRequest();
            updateRequest.setStatus(AssignmentStatus.FAILED);
            updateRequest.setFailureReason("Customer not available");

            mockMvc.perform(patch("/api/v1/delivery-assignments/" + assignment.getId() + "/status")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(updateRequest))
                    .with(authBuilder.build()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value(AssignmentStatus.FAILED.name()));

            var updatedAssignment = assignmentRepository.findById(assignment.getId()).orElseThrow();
            assertThat(updatedAssignment.getStatus()).isEqualTo(AssignmentStatus.FAILED);
            assertThat(updatedAssignment.getFailureReason()).isEqualTo("Customer not available");
        }
    }

    // Helper methods
    private DeliveryPartner createTestPartner(String name) {
        var partner = DeliveryPartner.builder()
                .userId(UUID.randomUUID())
                .name(name)
                .mobile("+91" + (System.currentTimeMillis() % 10000000000L))
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
