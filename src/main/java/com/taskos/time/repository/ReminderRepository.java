package com.taskos.time.repository;

import com.taskos.time.entity.Reminder;
import com.taskos.time.entity.ReminderStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReminderRepository extends JpaRepository<Reminder, UUID> {
    List<Reminder> findAllByOwnerIdOrderByScheduledForAsc(UUID ownerId);
    Optional<Reminder> findByIdAndOwnerId(UUID id, UUID ownerId);
    long countByOwnerIdAndStatus(UUID ownerId, ReminderStatus status);
    @Query("select reminder from Reminder reminder where reminder.status = com.taskos.time.entity.ReminderStatus.PENDING and reminder.scheduledFor <= :now order by reminder.scheduledFor")
    List<Reminder> findDue(@Param("now") Instant now, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select reminder from Reminder reminder where reminder.id = :id")
    Optional<Reminder> findLockedById(@Param("id") UUID id);
}
