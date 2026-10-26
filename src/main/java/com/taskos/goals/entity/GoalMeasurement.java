package com.taskos.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "goal_measurements", schema = "task_os")
public class GoalMeasurement {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "goal_id", nullable = false, updatable = false) private UUID goalId;
    @Column(name = "recorded_value", nullable = false, precision = 16, scale = 4) private BigDecimal recordedValue;
    @Column(name = "target_snapshot", precision = 16, scale = 4) private BigDecimal targetSnapshot;
    @Column(name = "measurement_date", nullable = false) private LocalDate measurementDate;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt;
    @Column(nullable = false, length = 1000) private String notes;
    protected GoalMeasurement() { }
    public static GoalMeasurement record(UUID ownerId, Goal goal, BigDecimal value, LocalDate date, String notes, Instant now) {
        var item = new GoalMeasurement(); item.id = UUID.randomUUID(); item.ownerId = ownerId; item.goalId = goal.getId();
        item.recordedValue = value; item.targetSnapshot = goal.getTargetValue(); item.measurementDate = date;
        item.notes = notes == null ? "" : notes.strip(); item.recordedAt = now; return item;
    }
    public UUID getId() { return id; } public UUID getGoalId() { return goalId; } public BigDecimal getRecordedValue() { return recordedValue; }
    public BigDecimal getTargetSnapshot() { return targetSnapshot; } public LocalDate getMeasurementDate() { return measurementDate; }
    public Instant getRecordedAt() { return recordedAt; } public String getNotes() { return notes; }
}
