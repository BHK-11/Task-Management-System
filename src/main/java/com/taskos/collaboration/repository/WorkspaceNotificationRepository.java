package com.taskos.collaboration.repository;

import com.taskos.collaboration.entity.WorkspaceNotification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkspaceNotificationRepository extends JpaRepository<WorkspaceNotification, UUID> {
    List<WorkspaceNotification> findAllByRecipientIdOrderByDeliveredAtDesc(UUID recipientId, Pageable pageable);
    Optional<WorkspaceNotification> findByIdAndRecipientId(UUID id, UUID recipientId);
    long countByRecipientIdAndReadAtIsNull(UUID recipientId);
}
