package com.taskos.time.repository;

import com.taskos.time.entity.FocusInterval;
import com.taskos.time.entity.FocusIntervalState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FocusIntervalRepository extends JpaRepository<FocusInterval, UUID> {
    List<FocusInterval> findAllByOwnerIdAndSessionIdOrderByStartedAtAsc(UUID ownerId, UUID sessionId);
    Optional<FocusInterval> findFirstByOwnerIdAndSessionIdAndStateOrderByStartedAtDesc(UUID ownerId, UUID sessionId, FocusIntervalState state);
}
