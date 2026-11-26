package com.civicfix.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndIsReadOrderByCreatedAtDesc(UUID recipientId, boolean isRead, Pageable pageable);

    long countByRecipientIdAndIsReadFalse(UUID recipientId);

    Optional<Notification> findByIdAndRecipientId(UUID id, UUID recipientId);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.recipient.id = :recipientId AND n.isRead = false")
    int markAllAsReadByRecipientId(@Param("recipientId") UUID recipientId, @Param("now") OffsetDateTime now);

    @Modifying
    @Query("UPDATE Notification n SET n.isRead = true, n.readAt = :now WHERE n.complaint.id = :complaintId AND n.notificationType = :type AND n.isRead = false")
    int markComplaintNotificationsAsReadByType(
            @Param("complaintId") UUID complaintId,
            @Param("type") NotificationType type,
            @Param("now") OffsetDateTime now);

    boolean existsByRecipientIdAndComplaintIdAndNotificationTypeAndCreatedAtAfter(
            UUID recipientId, UUID complaintId, NotificationType notificationType, OffsetDateTime after);
}
