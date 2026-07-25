package com.farm2home.notification.controller;

import com.farm2home.common.web.dto.response.ApiResponse;
import com.farm2home.notification.dto.response.NotificationLogResponse;
import com.farm2home.notification.service.impl.NotificationServiceImpl;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationLogController {

    private final NotificationServiceImpl service;

    @GetMapping("/logs")
    @PreAuthorize("hasAnyAuthority('FARM_MANAGER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Page<NotificationLogResponse>>> findByRecipient(
            @RequestParam UUID recipientId, Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Notification logs retrieved successfully", service.findByRecipient(recipientId, pageable)));
    }

    @GetMapping("/logs/{id}")
    @PreAuthorize("hasAnyAuthority('FARM_MANAGER','SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<NotificationLogResponse>> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Notification log retrieved successfully", service.findById(id)));
    }
}
