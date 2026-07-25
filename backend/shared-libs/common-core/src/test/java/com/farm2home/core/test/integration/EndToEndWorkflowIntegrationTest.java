package com.farm2home.core.test.integration;

import com.farm2home.core.test.BaseIntegrationTest;
import com.farm2home.core.test.AuthenticationTestBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration tests for complete workflows across multiple services.
 * Tests order creation → payment → delivery assignment flow.
 */
@DisplayName("End-to-End Integration Tests")
class EndToEndWorkflowIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private UUID customerId;
    private AuthenticationTestBuilder customerAuth;
    private AuthenticationTestBuilder adminAuth;

    @BeforeEach
    void setUp() {
        customerId = UUID.randomUUID();
        customerAuth = new AuthenticationTestBuilder()
                .withUserId(customerId)
                .withUsername("customer@farm2home.com")
                .withRoles("CUSTOMER");

        adminAuth = new AuthenticationTestBuilder()
                .withUserId(UUID.randomUUID())
                .withUsername("admin@farm2home.com")
                .withRoles("ADMIN");
    }

    @Test
    @DisplayName("Should complete full order to delivery workflow")
    void shouldCompleteFullWorkflow() throws Exception {
        // Step 1: Create an order
        var orderRequest = """
                {
                  "orderDate": "%s",
                  "items": [
                    {
                      "milkType": "FULL_CREAM",
                      "quantity": 2
                    }
                  ]
                }
                """.formatted(LocalDate.now());

        var orderResponse = mockMvc.perform(post("/api/v1/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(orderRequest)
                .with(customerAuth.build()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var orderId = objectMapper.readTree(orderResponse).get("id").asText();

        // Step 2: Verify order was created in database
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                .with(customerAuth.build()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        // Step 3: Initiate payment for the order
        var paymentRequest = """
                {
                  "orderId": "%s",
                  "amount": 160.0,
                  "paymentMethod": "UPI",
                  "upiId": "customer@upi"
                }
                """.formatted(orderId);

        var paymentResponse = mockMvc.perform(post("/api/v1/payments/initiate")
                .contentType(MediaType.APPLICATION_JSON)
                .content(paymentRequest)
                .with(customerAuth.build()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var paymentId = objectMapper.readTree(paymentResponse).get("id").asText();

        // Step 4: Process payment callback (simulating external payment gateway)
        var callbackRequest = """
                {
                  "paymentId": "%s",
                  "status": "COMPLETED",
                  "referenceId": "TXN-12345"
                }
                """.formatted(paymentId);

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/callback")
                .contentType(MediaType.APPLICATION_JSON)
                .content(callbackRequest))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Step 5: Register delivery partner (admin only)
        var partnerRequest = """
                {
                  "name": "John Delivery",
                  "phone": "+919876543210",
                  "email": "john@delivery.com",
                  "vehicle": "2-Wheeler",
                  "licenseNumber": "LIC123456"
                }
                """;

        var partnerResponse = mockMvc.perform(post("/api/v1/delivery-partners")
                .contentType(MediaType.APPLICATION_JSON)
                .content(partnerRequest)
                .with(adminAuth.build()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var partnerId = objectMapper.readTree(partnerResponse).get("id").asText();

        // Step 6: Create delivery assignment
        var assignmentRequest = """
                {
                  "orderId": "%s",
                  "deliveryPartnerId": "%s",
                  "address": "123 Customer St, City"
                }
                """.formatted(orderId, partnerId);

        var assignmentResponse = mockMvc.perform(post("/api/v1/delivery-assignments")
                .contentType(MediaType.APPLICATION_JSON)
                .content(assignmentRequest)
                .with(adminAuth.build()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andReturn()
                .getResponse()
                .getContentAsString();

        var assignmentId = objectMapper.readTree(assignmentResponse).get("id").asText();

        // Step 7: Update assignment status to PICKED_UP
        var pickupRequest = """
                {
                  "status": "PICKED_UP"
                }
                """;

        mockMvc.perform(patch("/api/v1/delivery-assignments/" + assignmentId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(pickupRequest)
                .with(adminAuth.build()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PICKED_UP"));

        // Step 8: Update assignment status to DELIVERED
        var deliveryRequest = """
                {
                  "status": "DELIVERED"
                }
                """;

        mockMvc.perform(patch("/api/v1/delivery-assignments/" + assignmentId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(deliveryRequest)
                .with(adminAuth.build()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DELIVERED"));

        // Step 9: Update order status to COMPLETED
        var completeRequest = """
                {
                  "status": "COMPLETED"
                }
                """;

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content(completeRequest)
                .with(customerAuth.build()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"));

        // Verification: Confirm final state
        mockMvc.perform(get("/api/v1/orders/" + orderId)
                .with(customerAuth.build()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("COMPLETED"))
                .andExpect(jsonPath("$.id").value(orderId));
    }
}
