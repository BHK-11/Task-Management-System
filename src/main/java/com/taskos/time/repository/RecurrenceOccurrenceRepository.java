package com.taskos.time.repository;

import com.taskos.time.entity.RecurrenceOccurrence;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RecurrenceOccurrenceRepository extends JpaRepository<RecurrenceOccurrence, UUID> {
    Optional<RecurrenceOccurrence> findByRuleIdAndOccurrenceDate(UUID ruleId, LocalDate date);
    List<RecurrenceOccurrence> findTop100ByOwnerIdOrderByOccurrenceDateDesc(UUID ownerId);
    long countByRuleId(UUID ruleId);
}
