package com.taskos.automation.service;

import com.taskos.automation.entity.AutomationApprovalStatus;
import com.taskos.automation.entity.AutomationExecution;
import com.taskos.automation.entity.AutomationExecutionStatus;
import com.taskos.automation.repository.AutomationApprovalRepository;
import com.taskos.automation.repository.AutomationExecutionRepository;
import com.taskos.collaboration.service.WorkspaceAuthorizationService;
import com.taskos.collaboration.service.WorkspacePermission;
import java.time.Clock;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationApprovalService {
    private final AutomationApprovalRepository approvals;
    private final AutomationExecutionRepository executions;
    private final WorkspaceAuthorizationService authorization;
    private final Clock clock;

    public AutomationApprovalService(AutomationApprovalRepository approvals, AutomationExecutionRepository executions,
            WorkspaceAuthorizationService authorization, Clock clock) {
        this.approvals = approvals; this.executions = executions; this.authorization = authorization; this.clock = clock;
    }

    @Transactional
    public void decide(UUID actorId, UUID workspaceId, UUID approvalId, boolean approve, String reason) {
        authorization.requireLocked(actorId, workspaceId, WorkspacePermission.EDIT_SETTINGS);
        var request = approvals.findByIdAndScopeId(approvalId, workspaceId)
                .orElseThrow(() -> new AutomationRuleException("That approval is no longer available."));
        var execution = executions.findById(request.getExecutionId())
                .orElseThrow(() -> new AutomationRuleException("That execution is no longer available."));
        if (execution.getStatus() != AutomationExecutionStatus.WAITING_APPROVAL)
            throw new AutomationRuleException("This approval is no longer pending.");
        if (reason != null && reason.strip().length() > 240) throw new AutomationRuleException("Keep the decision note to 240 characters or fewer.");
        var now = clock.instant();
        try { request.decide(actorId, approve, reason == null ? null : reason.strip(), now); }
        catch (IllegalStateException ex) { throw new AutomationRuleException(ex.getMessage()); }
        if (approve) execution.approve(now); else execution.reject(now);
    }

    @Transactional
    public int expirePending() {
        var now = clock.instant(); int count = 0;
        for (var request : approvals.findAllByStatusAndExpiresAtLessThanEqual(AutomationApprovalStatus.PENDING,
                now, PageRequest.of(0, 100))) {
            if (!request.expire(now)) continue;
            executions.findById(request.getExecutionId()).filter(execution -> execution.getStatus() == AutomationExecutionStatus.WAITING_APPROVAL)
                    .ifPresent(execution -> execution.expire(now));
            count++;
        }
        return count;
    }
}
