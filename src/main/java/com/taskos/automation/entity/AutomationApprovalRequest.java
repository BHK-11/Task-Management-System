package com.taskos.automation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_approvals", schema = "task_os")
public class AutomationApprovalRequest {
    @Id private UUID id;
    @Column(name = "execution_id", nullable = false, unique = true, updatable = false) private UUID executionId;
    @Column(name = "scope_id", nullable = false, updatable = false) private UUID scopeId;
    @Column(name = "requested_by", nullable = false, updatable = false) private UUID requestedBy;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AutomationApprovalStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "expires_at", nullable = false, updatable = false) private Instant expiresAt;
    @Column(name = "decided_at") private Instant decidedAt;
    @Column(name = "decided_by") private UUID decidedBy;
    @Column(name = "decision_reason", length = 240) private String decisionReason;
    @Version private Long version;

    protected AutomationApprovalRequest() { }
    public static AutomationApprovalRequest create(UUID executionId, UUID scopeId, UUID requestedBy, Instant now, Instant expiresAt) {
        AutomationApprovalRequest request = new AutomationApprovalRequest(); request.id = UUID.randomUUID();
        request.executionId = executionId; request.scopeId = scopeId; request.requestedBy = requestedBy;
        request.status = AutomationApprovalStatus.PENDING; request.createdAt = now; request.expiresAt = expiresAt; return request;
    }
    public void decide(UUID actorId, boolean approve, String reason, Instant now) {
        if (status != AutomationApprovalStatus.PENDING || !now.isBefore(expiresAt)) throw new IllegalStateException("This approval is no longer pending.");
        status = approve ? AutomationApprovalStatus.APPROVED : AutomationApprovalStatus.REJECTED;
        decidedBy = actorId; decidedAt = now; decisionReason = reason;
    }
    public boolean expire(Instant now) {
        if (status != AutomationApprovalStatus.PENDING || now.isBefore(expiresAt)) return false;
        status = AutomationApprovalStatus.EXPIRED; decidedAt = now; return true;
    }
    public UUID getId() { return id; }
    public UUID getExecutionId() { return executionId; }
    public UUID getScopeId() { return scopeId; }
    public UUID getRequestedBy() { return requestedBy; }
    public AutomationApprovalStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public UUID getDecidedBy() { return decidedBy; }
    public String getDecisionReason() { return decisionReason; }
}
