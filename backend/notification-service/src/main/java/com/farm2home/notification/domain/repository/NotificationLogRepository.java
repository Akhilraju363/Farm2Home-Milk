package com.farm2home.notification.domain.repository;

import com.farm2home.notification.domain.entity.NotificationLog;
import com.farm2home.notification.domain.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, UUID> {

    Page<NotificationLog> findAllByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    List<NotificationLog> findAllByStatusOrderByCreatedAtAsc(NotificationStatus status);

    // Scopes a single notification lookup to its actual recipient - see
    // NotificationServiceImpl.markAsRead(), which relies on this to enforce that a caller can
    // never mark another recipient's notification as read by guessing its id.
    Optional<NotificationLog> findByIdAndRecipientId(UUID id, UUID recipientId);

    @Modifying
    @Query("UPDATE NotificationLog n SET n.isRead = true WHERE n.recipientId = :recipientId AND n.isRead = false")
    void markAllAsReadForRecipient(UUID recipientId);
}
