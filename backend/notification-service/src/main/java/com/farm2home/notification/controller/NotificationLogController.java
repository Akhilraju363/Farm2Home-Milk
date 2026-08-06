package com.farm2home.notification.controller;

import com.farm2home.common.core.constants.SecurityConstants;
import com.farm2home.common.core.dashboard.NotificationSummaryItem;
import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.notification.config.UserPrincipal;
import com.farm2home.notification.dto.response.NotificationLogResponse;
import com.farm2home.notification.service.impl.NotificationServiceImpl;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/** Read-only history for every dispatched notification (SMS, EMAIL, PUSH) - one row per channel
 *  per event, persisted by NotificationServiceImpl.sendForChannel() regardless of channel or
 *  outcome (SENT/FAILED). Sending itself has no REST API - it's entirely event-driven, triggered
 *  by Kafka events from order/delivery/payment/subscription-service (see
 *  docs/PUSH_NOTIFICATION_INTEGRATION.md). */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Read-only notification delivery history (SMS/EMAIL/PUSH)")
@SecurityRequirement(name = "bearerAuth")
@RequiredArgsConstructor
public class NotificationLogController {

    private final NotificationServiceImpl service;

    @GetMapping("/logs")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Get a recipient's notification history",
            description = "All notification log entries for one recipient (customer), across every "
                    + "channel (SMS/EMAIL/PUSH) and event type, most recent first. PUSH entries use the "
                    + "recipient's own customer id as the log's recipient field (see PushService wiring - "
                    + "push has no separate device-token identifier in this codebase).")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Notification logs retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN", content = @Content)
    })
    public ResponseEntity<ApiResponse<Page<NotificationLogResponse>>> findByRecipient(
            @Parameter(description = "Customer id the notifications were sent to") @RequestParam UUID recipientId,
            Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Notification logs retrieved successfully", service.findByRecipient(recipientId, pageable)));
    }

    @GetMapping("/logs/{id}")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_FARM_MANAGER + "','" + SecurityConstants.ROLE_SUPER_ADMIN + "')")
    @Operation(summary = "Get a single notification log entry by id")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Notification log entry retrieved",
                content = @Content(mediaType = MediaType.APPLICATION_JSON_VALUE,
                        examples = @ExampleObject(value = """
                                {
                                  "success": true,
                                  "message": "Notification log retrieved successfully",
                                  "data": {
                                    "id": "e5f6a7b8-1c2d-4e3f-9a8b-7c6d5e4f3a2b",
                                    "recipientId": "3b1e6a2c-2f9a-4b8b-9c2e-0a1a2b3c4d5e",
                                    "channel": "EMAIL",
                                    "eventType": "DELIVERY_ASSIGNED",
                                    "recipient": "customer@example.com",
                                    "subject": "Your order is on its way!",
                                    "message": "Hi Ramesh, your order #1234 has been assigned to a delivery partner.",
                                    "status": "SENT",
                                    "sentAt": "2026-07-31T09:15:05",
                                    "createdAt": "2026-07-31T09:15:00"
                                  }
                                }"""))),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks FARM_MANAGER/SUPER_ADMIN", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No notification log entry with that id", content = @Content)
    })
    public ResponseEntity<ApiResponse<NotificationLogResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Notification log retrieved successfully", service.findById(id)));
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyAuthority('" + SecurityConstants.ROLE_SUPER_ADMIN + "', '" + SecurityConstants.ROLE_FARM_MANAGER + "', '" + SecurityConstants.ROLE_DELIVERY_MANAGER + "')")
    @Operation(summary = "Get recent notifications for the dashboard",
            description = "The most recent notification log entries across every recipient and channel, "
                    + "for an operations/dashboard feed - not scoped to one customer, unlike GET /logs.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Recent notifications retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403",
                description = "Caller lacks SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<NotificationSummaryItem>>> getSummary(
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success("Recent notifications retrieved successfully", service.getRecent(limit)));
    }

    @GetMapping("/me")
    @Operation(summary = "Get the caller's own recent notifications",
            description = "Self-service equivalent of GET /summary, scoped to the caller's own "
                    + "notifications only (recipientId = the caller's user id, resolved from the bearer "
                    + "token - never a client-supplied value). Open to any authenticated user, not just "
                    + "SUPER_ADMIN/FARM_MANAGER/DELIVERY_MANAGER, since unlike /summary this can never "
                    + "expose another recipient's notifications.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200",
                description = "Recent notifications retrieved (possibly empty)"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<List<NotificationSummaryItem>>> getMyRecent(
            @AuthenticationPrincipal UserPrincipal principal,
            @RequestParam(defaultValue = "10") int limit) {
        return ResponseEntity.ok(ApiResponse.success(
                "Recent notifications retrieved successfully", service.getMyRecent(principal.userId(), limit)));
    }

    @PatchMapping("/{id}/read")
    @Operation(summary = "Mark one of the caller's own notifications as read",
            description = "Ownership is enforced server-side (recipientId must match the caller) - "
                    + "404 if the id doesn't exist or belongs to someone else, never a 403 that would "
                    + "confirm the id exists.")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Marked as read"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404",
                description = "No such notification for this caller", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> markAsRead(
            @AuthenticationPrincipal UserPrincipal principal, @PathVariable UUID id) {
        service.markAsRead(principal.userId(), id);
        return ResponseEntity.ok(ApiResponse.success("Notification marked as read", null));
    }

    @PatchMapping("/read-all")
    @Operation(summary = "Mark all of the caller's own notifications as read")
    @ApiResponses({
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Marked as read"),
        @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "401",
                description = "Missing or invalid bearer token", content = @Content)
    })
    public ResponseEntity<ApiResponse<Void>> markAllAsRead(@AuthenticationPrincipal UserPrincipal principal) {
        service.markAllAsRead(principal.userId());
        return ResponseEntity.ok(ApiResponse.success("All notifications marked as read", null));
    }
}
