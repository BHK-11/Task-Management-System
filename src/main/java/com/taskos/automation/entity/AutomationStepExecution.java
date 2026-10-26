package com.taskos.automation.entity;

import com.taskos.automation.dto.AutomationActionType;
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
@Table(name = "automation_step_executions", schema = "task_os")
public class AutomationStepExecution {
    @Id private UUID id;
    @Column(name = "execution_id", nullable = false, updatable = false) private UUID executionId;
    @Column(name = "step_index", nullable = false, updatable = false) private int stepIndex;
    @Enumerated(EnumType.STRING) @Column(name = "action_type", nullable = false, length = 32, updatable = false) private AutomationActionType actionType;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private AutomationStepStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "finished_at") private Instant finishedAt;
    @Column(name = "result_summary", length = 240) private String resultSummary;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Column(name = "idempotency_key", nullable = false, length = 100, updatable = false) private String idempotencyKey;
    @Version private Long version;

    protected AutomationStepExecution() { }
    public static AutomationStepExecution create(UUID executionId, int stepIndex, AutomationActionType type, Instant now) {
        AutomationStepExecution step = new AutomationStepExecution(); step.id = UUID.randomUUID();
        step.executionId = executionId; step.stepIndex = stepIndex; step.actionType = type;
        step.status = AutomationStepStatus.PENDING; step.idempotencyKey = executionId + ":" + stepIndex;
        step.startedAt = now; return step;
    }
    public void start(Instant now) { status = AutomationStepStatus.RUNNING; attemptCount++; if (startedAt == null) startedAt = now; }
    public void succeed(Instant now, String summary) { status = AutomationStepStatus.SUCCEEDED; finishedAt = now; resultSummary = summary; errorCode = null; }
    public void fail(Instant now, String code, boolean permanent) { status = permanent ? AutomationStepStatus.FAILED_PERMANENT : AutomationStepStatus.FAILED_RETRYABLE; finishedAt = now; errorCode = code; }
    public void prepareRetry() {
        if (status == AutomationStepStatus.FAILED_PERMANENT || status == AutomationStepStatus.FAILED_RETRYABLE) {
            status = AutomationStepStatus.PENDING; finishedAt = null; errorCode = null;
        }
    }
    public UUID getId() { return id; }
    public UUID getExecutionId() { return executionId; }
    public int getStepIndex() { return stepIndex; }
    public AutomationActionType getActionType() { return actionType; }
    public AutomationStepStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getFinishedAt() { return finishedAt; }
    public String getResultSummary() { return resultSummary; }
    public String getErrorCode() { return errorCode; }
    public String getIdempotencyKey() { return idempotencyKey; }
}
