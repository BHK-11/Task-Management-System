package com.taskos.automation.repository;

import com.taskos.automation.entity.AutomationSchedule;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AutomationScheduleRepository extends JpaRepository<AutomationSchedule, UUID> {
    Optional<AutomationSchedule> findByRuleId(UUID ruleId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<AutomationSchedule> findAllByActiveTrueAndNextFireAtLessThanEqualOrderByNextFireAtAsc(
            Instant now, Pageable pageable);
}
