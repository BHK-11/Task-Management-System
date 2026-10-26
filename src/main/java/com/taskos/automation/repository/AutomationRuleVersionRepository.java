package com.taskos.automation.repository;

import com.taskos.automation.entity.AutomationRuleVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRuleVersionRepository extends JpaRepository<AutomationRuleVersion, UUID> {
    List<AutomationRuleVersion> findAllByRuleIdOrderByVersionNumberDesc(UUID ruleId);
    Optional<AutomationRuleVersion> findByIdAndRuleId(UUID id, UUID ruleId);
    Optional<AutomationRuleVersion> findFirstByRuleIdOrderByVersionNumberDesc(UUID ruleId);
}
