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
@Table(name = "automation_outbox", schema = "task_os")
public class AutomationOutboxEvent {
    @Id private UUID id;
    @Enumerated(EnumType.STRING) @Column(name = "trigger_type", nullable = false, length = 32, updatable = false) private AutomationTriggerType triggerType;
    @Enumerated(EnumType.STRING) @Column(name = "scope_type", nullable = false, length = 16, updatable = false) private AutomationScopeType scopeType;
    @Column(name = "scope_id", nullable = false, updatable = false) private UUID scopeId;
    @Column(name = "actor_id", updatable = false) private UUID actorId;
    @Column(name = "entity_type", nullable = false, length = 32, updatable = false) private String entityType;
    @Column(name = "entity_id", updatable = false) private UUID entityId;
    @Column(name = "payload_json", nullable = false, length = 12000, updatable = false) private String payloadJson;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt;
    @Column(name = "correlation_id", nullable = false, updatable = false) private UUID correlationId;
    @Column(name = "causation_id", updatable = false) private UUID causationId;
    @Column(name = "origin_execution_id", updatable = false) private UUID originExecutionId;
    @Column(name = "chain_depth", nullable = false, updatable = false) private int chainDepth;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AutomationOutboxStatus status;
    @Column(name = "attempt_count", nullable = false) private int attemptCount;
    @Column(name = "available_at", nullable = false) private Instant availableAt;
    @Column(name = "claimed_at") private Instant claimedAt;
    @Column(name = "lease_expires_at") private Instant leaseExpiresAt;
    @Column(name = "processed_at") private Instant processedAt;
    @Column(name = "error_code", length = 64) private String errorCode;
    @Version private Long version;

    protected AutomationOutboxEvent() { }
    public static AutomationOutboxEvent create(AutomationTriggerType triggerType, AutomationScopeType scopeType,
            UUID scopeId, UUID actorId, String entityType, UUID entityId, String payloadJson, Instant now,
            UUID correlationId, UUID causationId, UUID originExecutionId, int chainDepth) {
        AutomationOutboxEvent event = new AutomationOutboxEvent(); event.id = UUID.randomUUID();
        event.triggerType = triggerType; event.scopeType = scopeType; event.scopeId = scopeId; event.actorId = actorId;
        event.entityType = entityType; event.entityId = entityId; event.payloadJson = payloadJson; event.occurredAt = now;
        event.correlationId = correlationId == null ? UUID.randomUUID() : correlationId; event.causationId = causationId;
        event.originExecutionId = originExecutionId; event.chainDepth = chainDepth; event.status = AutomationOutboxStatus.PENDING;
        event.availableAt = now; return event;
    }
    public void claim(Instant now, Instant leaseUntil) {
        status = AutomationOutboxStatus.PROCESSING; claimedAt = now; leaseExpiresAt = leaseUntil; attemptCount++;
    }
    public void processed(Instant now) { status = AutomationOutboxStatus.PROCESSED; processedAt = now; leaseExpiresAt = null; errorCode = null; }
    public void failed(Instant now, Instant retryAt, String safeErrorCode, int maxAttempts) {
        errorCode = safeErrorCode; leaseExpiresAt = null;
        if (attemptCount >= maxAttempts) { status = AutomationOutboxStatus.DEAD_LETTER; processedAt = now; }
        else { status = AutomationOutboxStatus.PENDING; availableAt = retryAt; }
    }
    public UUID getId() { return id; }
    public AutomationTriggerType getTriggerType() { return triggerType; }
    public AutomationScopeType getScopeType() { return scopeType; }
    public UUID getScopeId() { return scopeId; }
    public UUID getActorId() { return actorId; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public String getPayloadJson() { return payloadJson; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getCorrelationId() { return correlationId; }
    public UUID getCausationId() { return causationId; }
    public UUID getOriginExecutionId() { return originExecutionId; }
    public int getChainDepth() { return chainDepth; }
    public AutomationOutboxStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getLeaseExpiresAt() { return leaseExpiresAt; }
    public String getErrorCode() { return errorCode; }
}
