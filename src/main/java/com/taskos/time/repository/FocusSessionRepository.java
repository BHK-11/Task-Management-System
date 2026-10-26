package com.taskos.time.repository;

import com.taskos.time.entity.FocusSession;
import com.taskos.time.entity.FocusState;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FocusSessionRepository extends JpaRepository<FocusSession, UUID> {
    Optional<FocusSession> findByIdAndOwnerId(UUID id, UUID ownerId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select session from FocusSession session where session.id = :id and session.ownerId = :ownerId")
    Optional<FocusSession> findLockedByIdAndOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
    Optional<FocusSession> findFirstByOwnerIdAndStateInOrderByStartedAtDesc(UUID ownerId, Collection<FocusState> states);
    List<FocusSession> findTop50ByOwnerIdOrderByStartedAtDesc(UUID ownerId);
    List<FocusSession> findAllByOwnerIdAndStartedAtGreaterThanEqualAndStartedAtLessThanOrderByStartedAtAsc(
            UUID ownerId, java.time.Instant start, java.time.Instant end);
}
