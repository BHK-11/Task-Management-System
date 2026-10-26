package com.taskos.automation.repository;

import com.taskos.automation.entity.AutomationStepExecution;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AutomationStepExecutionRepository extends JpaRepository<AutomationStepExecution, UUID> {
    List<AutomationStepExecution> findAllByExecutionIdOrderByStepIndexAsc(UUID executionId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<AutomationStepExecution> findByExecutionIdAndStepIndex(UUID executionId, int stepIndex);
}
