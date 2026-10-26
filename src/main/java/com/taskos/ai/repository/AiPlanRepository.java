package com.taskos.ai.repository;

import com.taskos.ai.entity.AiPlan;
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

public interface AiPlanRepository extends JpaRepository<AiPlan, UUID> {
    long countByOwner_IdAndCreatedAtAfter(UUID ownerId, Instant since);

    long countByOwner_IdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(UUID ownerId, Instant from, Instant until);

    @Query("""
            select count(plan), coalesce(sum(plan.inputTokens), 0), coalesce(sum(plan.outputTokens), 0)
            from AiPlan plan where plan.owner.id = :ownerId and plan.createdAt >= :from
            """)
    List<Object[]> summarizeUsage(@Param("ownerId") UUID ownerId, @Param("from") Instant from);

    @Query("""
            select count(step) from AiPlanStep step join step.plan plan
            where plan.owner.id = :ownerId and plan.createdAt >= :from and plan.createdAt < :until
              and step.resultTaskId is not null and step.status = com.taskos.ai.dto.AiPlanStepStatus.COMPLETED
            """)
    long countVerifiedTasks(@Param("ownerId") UUID ownerId, @Param("from") Instant from, @Param("until") Instant until);

    List<AiPlan> findAllByOwner_IdOrderByCreatedAtDesc(UUID ownerId, Pageable pageable);

    @Query("select distinct plan from AiPlan plan left join fetch plan.steps where plan.id = :planId and plan.owner.id = :ownerId")
    Optional<AiPlan> findByIdAndOwnerId(@Param("planId") UUID planId, @Param("ownerId") UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from AiPlan plan where plan.id = :planId and plan.owner.id = :ownerId")
    Optional<AiPlan> findLockedByIdAndOwnerId(@Param("planId") UUID planId, @Param("ownerId") UUID ownerId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from AiPlan plan where plan.id = :planId")
    Optional<AiPlan> findLockedById(@Param("planId") UUID planId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select plan from AiPlan plan where plan.status = com.taskos.ai.dto.AiPlanStatus.QUEUED "
            + "or (plan.status = com.taskos.ai.dto.AiPlanStatus.RUNNING and plan.executionLeaseUntil <= :now) "
            + "order by plan.createdAt asc")
    List<AiPlan> lockNextRunnable(@Param("now") Instant now, Pageable pageable);
}
