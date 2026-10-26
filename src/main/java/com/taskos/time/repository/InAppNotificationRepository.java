package com.taskos.time.repository;

import com.taskos.time.entity.InAppNotification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InAppNotificationRepository extends JpaRepository<InAppNotification, UUID> {
    List<InAppNotification> findAllByOwnerIdOrderByDeliveredAtDesc(UUID ownerId, Pageable pageable);
    Optional<InAppNotification> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<InAppNotification> findByReminderId(UUID reminderId);
    long countByOwnerIdAndReadAtIsNull(UUID ownerId);
}
