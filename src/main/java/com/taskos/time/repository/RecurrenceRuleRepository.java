package com.taskos.time.repository;

import com.taskos.time.entity.RecurrenceRule;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RecurrenceRuleRepository extends JpaRepository<RecurrenceRule, UUID> {
    List<RecurrenceRule> findAllByOwnerIdOrderByCreatedAtDesc(UUID ownerId);
    List<RecurrenceRule> findTop100ByPausedFalseAndEndedFalseAndNextOccurrenceDateLessThanEqualOrderByNextOccurrenceDate(LocalDate date);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select rule from RecurrenceRule rule where rule.id = :id and rule.ownerId = :ownerId")
    Optional<RecurrenceRule> findLockedByIdAndOwner(@Param("id") UUID id, @Param("ownerId") UUID ownerId);
}
