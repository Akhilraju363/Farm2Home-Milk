package com.farm2home.notification.domain.repository;

import com.farm2home.notification.domain.entity.NotificationLog;
import com.farm2home.notification.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    List<NotificationLog> findAllByStatusOrderByCreatedAtAsc(NotificationStatus status);
}
