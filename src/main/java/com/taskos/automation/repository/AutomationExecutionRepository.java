package com.taskos.automation.repository;

import com.taskos.automation.entity.AutomationExecution;
import com.taskos.automation.entity.AutomationExecutionStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AutomationExecutionRepository extends JpaRepository<AutomationExecution, UUID> {
    @Query("""
            select execution.status, count(execution), count(case when execution.attemptCount > 0 then execution.id end)
            from AutomationExecution execution
            where execution.scopeType = com.taskos.automation.entity.AutomationScopeType.PERSONAL
              and execution.scopeId = :ownerId and execution.createdAt >= :from and execution.createdAt < :until
            group by execution.status
            """)
    List<Object[]> personalAnalytics(@Param("ownerId") UUID ownerId, @Param("from") Instant from,
            @Param("until") Instant until);

    boolean existsByRuleVersionIdAndTriggerEventId(UUID ruleVersionId, UUID triggerEventId);
    List<AutomationExecution> findAllByRuleIdOrderByCreatedAtDesc(UUID ruleId, Pageable pageable);
    List<AutomationExecution> findAllByScopeTypeAndScopeIdOrderByCreatedAtDesc(
            com.taskos.automation.entity.AutomationScopeType scopeType, UUID scopeId, Pageable pageable);
    Optional<AutomationExecution> findByIdAndScopeTypeAndScopeId(UUID id,
            com.taskos.automation.entity.AutomationScopeType scopeType, UUID scopeId);
    long countByStatus(AutomationExecutionStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AutomationExecution> findFirstByStatusAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(
            AutomationExecutionStatus status, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AutomationExecution> findFirstByStatusAndLeaseExpiresAtLessThanEqualOrderByCreatedAtAsc(
            AutomationExecutionStatus status, Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from AutomationExecution e where (e.status in :ready and e.nextAttemptAt <= :now) " +
            "or (e.status = :running and e.leaseExpiresAt <= :now) order by e.createdAt asc")
    List<AutomationExecution> lockRunnable(@Param("ready") List<AutomationExecutionStatus> ready,
            @Param("running") AutomationExecutionStatus running, @Param("now") Instant now, Pageable pageable);
}
