package com.taskos.automation.repository;

import com.taskos.automation.entity.AutomationApprovalRequest;
import com.taskos.automation.entity.AutomationApprovalStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AutomationApprovalRepository extends JpaRepository<AutomationApprovalRequest, UUID> {
    List<AutomationApprovalRequest> findAllByScopeIdAndStatusOrderByCreatedAtAsc(UUID scopeId,
            AutomationApprovalStatus status, Pageable pageable);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AutomationApprovalRequest> findByIdAndScopeId(UUID id, UUID scopeId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AutomationApprovalRequest> findAllByStatusAndExpiresAtLessThanEqual(AutomationApprovalStatus status,
            Instant now, Pageable pageable);
}
