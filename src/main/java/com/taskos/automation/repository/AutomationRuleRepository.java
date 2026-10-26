package com.taskos.automation.repository;

import com.taskos.automation.entity.AutomationRule;
import com.taskos.automation.entity.AutomationRuleStatus;
import com.taskos.automation.entity.AutomationScopeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AutomationRuleRepository extends JpaRepository<AutomationRule, UUID> {
    List<AutomationRule> findAllByScopeTypeAndScopeIdOrderByUpdatedAtDesc(AutomationScopeType scopeType, UUID scopeId);
    List<AutomationRule> findAllByScopeTypeAndScopeIdAndStatusOrderByUpdatedAtDesc(AutomationScopeType scopeType, UUID scopeId, AutomationRuleStatus status);
    Optional<AutomationRule> findByIdAndScopeTypeAndScopeId(UUID id, AutomationScopeType scopeType, UUID scopeId);
    long countByScopeTypeAndScopeIdAndStatus(AutomationScopeType scopeType, UUID scopeId, AutomationRuleStatus status);
    List<AutomationRule> findAllByStatusOrderByUpdatedAtAsc(AutomationRuleStatus status, Pageable pageable);
}
