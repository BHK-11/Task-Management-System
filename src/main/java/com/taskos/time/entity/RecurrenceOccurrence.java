package com.taskos.time.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "recurrence_occurrences", schema = "task_os")
public class RecurrenceOccurrence {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "rule_id", nullable = false, updatable = false) private UUID ruleId;
    @Column(name = "task_id") private UUID taskId;
    @Column(name = "occurrence_date", nullable = false, updatable = false) private LocalDate occurrenceDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private OccurrenceStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected RecurrenceOccurrence() { }
    public static RecurrenceOccurrence generated(UUID ownerId, UUID ruleId, UUID taskId, LocalDate date, Instant now) {
        return build(ownerId, ruleId, taskId, date, OccurrenceStatus.GENERATED, now);
    }
    public static RecurrenceOccurrence skipped(UUID ownerId, UUID ruleId, LocalDate date, Instant now) {
        return build(ownerId, ruleId, null, date, OccurrenceStatus.SKIPPED, now);
    }
    public static RecurrenceOccurrence rescheduled(UUID ownerId, UUID ruleId, UUID taskId, LocalDate date, Instant now) {
        return build(ownerId, ruleId, taskId, date, OccurrenceStatus.RESCHEDULED, now);
    }
    private static RecurrenceOccurrence build(UUID ownerId, UUID ruleId, UUID taskId, LocalDate date, OccurrenceStatus status, Instant now) {
        RecurrenceOccurrence item = new RecurrenceOccurrence(); item.id = UUID.randomUUID(); item.ownerId = ownerId;
        item.ruleId = ruleId; item.taskId = taskId; item.occurrenceDate = date; item.status = status; item.createdAt = now; return item;
    }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getRuleId() { return ruleId; }
    public UUID getTaskId() { return taskId; }
    public LocalDate getOccurrenceDate() { return occurrenceDate; }
    public OccurrenceStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
}
