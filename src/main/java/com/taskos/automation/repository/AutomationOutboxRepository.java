package com.taskos.automation.repository;

import com.taskos.automation.entity.AutomationOutboxEvent;
import com.taskos.automation.entity.AutomationOutboxStatus;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AutomationOutboxRepository extends JpaRepository<AutomationOutboxEvent, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AutomationOutboxEvent> findFirstByStatusAndAvailableAtLessThanEqualOrderByOccurredAtAsc(
            AutomationOutboxStatus status, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AutomationOutboxEvent> findFirstByStatusAndLeaseExpiresAtLessThanEqualOrderByOccurredAtAsc(
            AutomationOutboxStatus status, Instant now);
    long countByStatus(AutomationOutboxStatus status);
}
