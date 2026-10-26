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
@Table(name = "automation_executions", schema = "task_os")
public class AutomationExecution {
    @Id private UUID id;
    @Column(name = "rule_id", nullable = false, updatable = false) private UUID ruleId;
    @Column(name = "rule_version_id", nullable = false, updatable = false) private UUID ruleVersionId;
    @Column(name = "trigger_event_id", nullable = false, updatable = false) private UUID triggerEventId;
    @Column(name = "scope_type", nullable = false, length = 16, updatable = false) @Enumerated(EnumType.STRING) private AutomationScopeType scopeType;
    @Column(name = "scope_id", nullable = false, updatable = false) private UUID scopeId;
    @Column(nullable = false, length = 24) @Enumerated(EnumType.STRING) private AutomationExecutionStatus status;
    @Column(name = "initiated_by", updatable = false) private UUID initiatedBy;
    @Column(name = "correlation_id", nullable = false, updatable = false) private UUID correlationId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "next_attempt_at", nullable = false) private Instant nextAttemptAt;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "lease_expires_at") private Instant leaseExpiresAt;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "last_error_code", length = 64) private String lastErrorCode;
    @Column(name = "safe_error_summary", length = 240) private String safeErrorSummary;
    @Version private Long version;

    protected AutomationExecution() { }
    public static AutomationExecution create(UUID ruleId, UUID versionId, UUID eventId, AutomationScopeType scopeType,
            UUID scopeId, UUID actorId, UUID correlationId, boolean waitForApproval, Instant now) {
        AutomationExecution execution = new AutomationExecution(); execution.id = UUID.randomUUID();
        execution.ruleId = ruleId; execution.ruleVersionId = versionId; execution.triggerEventId = eventId;
        execution.scopeType = scopeType; execution.scopeId = scopeId; execution.initiatedBy = actorId;
        execution.correlationId = correlationId; execution.status = waitForApproval
                ? AutomationExecutionStatus.WAITING_APPROVAL : AutomationExecutionStatus.PENDING;
        execution.createdAt = now; execution.nextAttemptAt = now; return execution;
    }
    public void claim(Instant now, Instant leaseUntil) {
        status = AutomationExecutionStatus.RUNNING; claimedAt = now; leaseExpiresAt = leaseUntil;
        if (startedAt == null) startedAt = now;
    }
    public void retry(Instant now, Instant retryAt, String code, String summary, boolean permanent) {
        attemptCount++; lastErrorCode = code; safeErrorSummary = summary; leaseExpiresAt = null;
        if (permanent) { status = AutomationExecutionStatus.FAILED_PERMANENT; completedAt = now; }
        else { status = AutomationExecutionStatus.FAILED_RETRYABLE; nextAttemptAt = retryAt; }
    }
    public void deadLetter(Instant now, String code, String summary) {
        attemptCount++; lastErrorCode = code; safeErrorSummary = summary;
        status = AutomationExecutionStatus.DEAD_LETTER; completedAt = now; leaseExpiresAt = null;
    }
    public void manualRetry(Instant now) {
        if (status != AutomationExecutionStatus.DEAD_LETTER && status != AutomationExecutionStatus.FAILED_RETRYABLE)
            throw new IllegalStateException("This execution is not eligible for retry.");
        status = AutomationExecutionStatus.PENDING; nextAttemptAt = now; completedAt = null;
        attemptCount = 0; lastErrorCode = null; safeErrorSummary = null; leaseExpiresAt = null;
    }
    public void succeed(Instant now) { status = AutomationExecutionStatus.SUCCEEDED; completedAt = now; leaseExpiresAt = null; lastErrorCode = null; safeErrorSummary = null; }
    public void waitForApproval() { status = AutomationExecutionStatus.WAITING_APPROVAL; leaseExpiresAt = null; }
    public void approve(Instant now) { if (status != AutomationExecutionStatus.WAITING_APPROVAL) throw new IllegalStateException("Execution is not waiting for approval."); status = AutomationExecutionStatus.PENDING; nextAttemptAt = now; }
    public void reject(Instant now) { status = AutomationExecutionStatus.REJECTED; completedAt = now; leaseExpiresAt = null; }
    public void cancel(Instant now) { status = AutomationExecutionStatus.CANCELLED; completedAt = now; leaseExpiresAt = null; }
    public void expire(Instant now) { if (status == AutomationExecutionStatus.WAITING_APPROVAL) { status = AutomationExecutionStatus.EXPIRED; completedAt = now; } }
    public UUID getId() { return id; }
    public UUID getRuleId() { return ruleId; }
    public UUID getRuleVersionId() { return ruleVersionId; }
    public UUID getTriggerEventId() { return triggerEventId; }
    public AutomationScopeType getScopeType() { return scopeType; }
    public UUID getScopeId() { return scopeId; }
    public AutomationExecutionStatus getStatus() { return status; }
    public UUID getInitiatedBy() { return initiatedBy; }
    public UUID getCorrelationId() { return correlationId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public int getAttemptCount() { return attemptCount; }
    public String getLastErrorCode() { return lastErrorCode; }
    public String getSafeErrorSummary() { return safeErrorSummary; }
}
