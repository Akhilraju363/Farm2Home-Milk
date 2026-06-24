package com.farm2home.notification.controller;

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
    public ResponseEntity<Page<NotificationLogResponse>> findByRecipient(
            @RequestParam UUID recipientId, Pageable pageable) {
        return ResponseEntity.ok(service.findByRecipient(recipientId, pageable));
    }

    @GetMapping("/logs/{id}")
    @PreAuthorize("hasAnyAuthority('FARM_MANAGER','SUPER_ADMIN')")
    public ResponseEntity<NotificationLogResponse> findById(@PathVariable UUID id) {
        return ResponseEntity.ok(service.findById(id));
    }
}
