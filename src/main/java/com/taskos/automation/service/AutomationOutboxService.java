package com.taskos.automation.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.taskos.automation.entity.AutomationOutboxEvent;
import com.taskos.automation.entity.AutomationOutboxStatus;
import com.taskos.automation.entity.AutomationScopeType;
import com.taskos.automation.entity.AutomationTriggerType;
import com.taskos.automation.repository.AutomationOutboxRepository;
import com.taskos.automation.repository.AutomationExecutionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationOutboxService {
    private static final int MAX_ATTEMPTS = 5;
    private final AutomationOutboxRepository events;
    private final AutomationExecutionRepository executions;
    private final ObjectMapper json;
    private final Clock clock;

    public AutomationOutboxService(AutomationOutboxRepository events, AutomationExecutionRepository executions,
            ObjectMapper json, Clock clock) {
        this.events = events; this.executions = executions; this.json = json; this.clock = clock;
    }

    @Transactional
    public UUID publishTaskEvent(AutomationTriggerType trigger, UUID actorId, UUID workspaceId, UUID taskId,
            Map<String, Object> payload, UUID causationId, UUID originExecutionId, int chainDepth) {
        AutomationScopeType scopeType = workspaceId == null ? AutomationScopeType.PERSONAL : AutomationScopeType.WORKSPACE;
        UUID scopeId = workspaceId == null ? actorId : workspaceId;
        if (taskId == null) throw new IllegalArgumentException("A task event needs a task.");
        return publish(scopeType, scopeId, actorId, trigger, "TASK", taskId, payload, causationId, originExecutionId, chainDepth);
    }

    @Transactional
    public UUID publishScheduledEvent(AutomationScopeType scopeType, UUID scopeId, UUID actorId,
            Map<String, Object> payload) {
        return publish(scopeType, scopeId, actorId, AutomationTriggerType.SCHEDULED, "SCHEDULE", null,
                payload, null, null, 0);
    }

    private UUID publish(AutomationScopeType scopeType, UUID scopeId, UUID actorId, AutomationTriggerType trigger,
            String entityType, UUID entityId, Map<String, Object> payload, UUID causationId,
            UUID originExecutionId, int chainDepth) {
        if (scopeType == null || scopeId == null || trigger == null) throw new IllegalArgumentException("An automation event needs a scope and trigger.");
        if (chainDepth < 0 || chainDepth > 5) throw new IllegalArgumentException("Automation event chain is too deep.");
        String payloadJson;
        try { payloadJson = json.writeValueAsString(payload == null ? Map.of() : payload); }
        catch (JacksonException ex) { throw new IllegalStateException("Could not encode the automation event.", ex); }
        UUID correlationId = originExecutionId == null ? null : executions.findById(originExecutionId)
                .map(com.taskos.automation.entity.AutomationExecution::getCorrelationId).orElse(null);
        var event = AutomationOutboxEvent.create(trigger, scopeType, scopeId, actorId, entityType, entityId,
                payloadJson, clock.instant(), correlationId, causationId, originExecutionId, chainDepth);
        return events.save(event).getId();
    }

    @Transactional
    public Optional<UUID> claimNext() {
        Instant now = clock.instant();
        AutomationOutboxEvent event = events.findFirstByStatusAndAvailableAtLessThanEqualOrderByOccurredAtAsc(
                AutomationOutboxStatus.PENDING, now).orElseGet(() -> events
                .findFirstByStatusAndLeaseExpiresAtLessThanEqualOrderByOccurredAtAsc(AutomationOutboxStatus.PROCESSING, now)
                .orElse(null));
        if (event == null) return Optional.empty();
        event.claim(now, now.plusSeconds(30));
        events.save(event);
        return Optional.of(event.getId());
    }

    @Transactional
    public void recordFailure(UUID eventId) {
        events.findById(eventId).ifPresent(event -> {
            Instant now = clock.instant();
            long delaySeconds = Math.min(900, 30L << Math.min(event.getAttemptCount(), 5));
            event.failed(now, now.plus(Duration.ofSeconds(delaySeconds)), "OUTBOX_PROCESSING_FAILED", MAX_ATTEMPTS);
        });
    }
}
