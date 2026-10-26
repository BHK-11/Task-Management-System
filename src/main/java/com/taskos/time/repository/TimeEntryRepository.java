package com.taskos.time.repository;

import com.taskos.time.entity.TimeEntry;
import com.taskos.time.entity.TimeEntryStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TimeEntryRepository extends JpaRepository<TimeEntry, UUID> {
    Optional<TimeEntry> findByIdAndOwnerId(UUID id, UUID ownerId);
    Optional<TimeEntry> findFirstByOwnerIdAndStatusInOrderByCreatedAtDesc(UUID ownerId, Collection<TimeEntryStatus> statuses);
    List<TimeEntry> findTop100ByOwnerIdOrderByStartedAtDesc(UUID ownerId);
    List<TimeEntry> findAllByOwnerIdAndStartedAtGreaterThanEqualAndStartedAtLessThan(UUID ownerId, java.time.Instant start, java.time.Instant end);
    List<TimeEntry> findAllByOwnerIdAndTaskId(UUID ownerId, UUID taskId);
    boolean existsByFocusSessionId(UUID focusSessionId);
}
