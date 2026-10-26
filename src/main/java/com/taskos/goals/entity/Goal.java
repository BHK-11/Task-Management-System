package com.taskos.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "goals", schema = "task_os")
public class Goal {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 2000) private String description;
    @Column(name = "specific_outcome", nullable = false, length = 500) private String specificOutcome;
    @Column(name = "measurement_definition", nullable = false, length = 240) private String measurementDefinition;
    @Column(name = "why_it_matters", nullable = false, length = 500) private String whyItMatters;
    @Column(name = "feasibility_notes", nullable = false, length = 500) private String feasibilityNotes;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 24) private GoalCategory category;
    @Enumerated(EnumType.STRING) @Column(name = "metric_type", nullable = false, length = 24) private GoalMetric metricType;
    @Column(name = "baseline_value", precision = 16, scale = 4) private BigDecimal baselineValue;
    @Column(name = "target_value", precision = 16, scale = 4) private BigDecimal targetValue;
    @Column(nullable = false, length = 40) private String unit;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "target_date") private LocalDate targetDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private GoalStatus status;
    @Column(name = "project_id") private UUID projectId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "archived_at") private Instant archivedAt;
    @Version private Long version;

    protected Goal() { }

    public static Goal create(UUID ownerId, String title, String description, String specificOutcome,
            String measurementDefinition, String whyItMatters, String feasibilityNotes, GoalCategory category,
            GoalMetric metricType, BigDecimal baselineValue, BigDecimal targetValue, String unit,
            LocalDate startDate, LocalDate targetDate, UUID projectId, Instant now) {
        Goal goal = new Goal(); goal.id = UUID.randomUUID(); goal.ownerId = ownerId; goal.title = title.strip();
        goal.description = text(description); goal.specificOutcome = text(specificOutcome);
        goal.measurementDefinition = text(measurementDefinition); goal.whyItMatters = text(whyItMatters);
        goal.feasibilityNotes = text(feasibilityNotes); goal.category = category == null ? GoalCategory.PERSONAL : category;
        goal.metricType = metricType; goal.baselineValue = baselineValue; goal.targetValue = targetValue;
        goal.unit = text(unit); goal.startDate = startDate; goal.targetDate = targetDate; goal.projectId = projectId;
        goal.status = GoalStatus.DRAFT; goal.createdAt = now; goal.updatedAt = now; return goal;
    }

    public void update(String title, String description, String specificOutcome, String measurementDefinition,
            String whyItMatters, String feasibilityNotes, GoalCategory category, GoalMetric metricType,
            BigDecimal baselineValue, BigDecimal targetValue, String unit, LocalDate startDate,
            LocalDate targetDate, UUID projectId, Instant now) {
        this.title = title.strip(); this.description = text(description); this.specificOutcome = text(specificOutcome);
        this.measurementDefinition = text(measurementDefinition); this.whyItMatters = text(whyItMatters);
        this.feasibilityNotes = text(feasibilityNotes); this.category = category; this.metricType = metricType;
        this.baselineValue = baselineValue; this.targetValue = targetValue; this.unit = text(unit);
        this.startDate = startDate; this.targetDate = targetDate; this.projectId = projectId; this.updatedAt = now;
    }

    public void transition(GoalStatus next, Instant now) {
        if (status != next && !status.canTransitionTo(next)) throw new IllegalArgumentException("That goal status change is not available.");
        status = next; completedAt = next == GoalStatus.COMPLETED ? now : null; updatedAt = now;
    }
    public void archive(Instant now) { archivedAt = now; updatedAt = now; }
    private static String text(String value) { return value == null ? "" : value.strip(); }

    public UUID getId() { return id; } public UUID getOwnerId() { return ownerId; }
    public String getTitle() { return title; } public String getDescription() { return description; }
    public String getSpecificOutcome() { return specificOutcome; } public String getMeasurementDefinition() { return measurementDefinition; }
    public String getWhyItMatters() { return whyItMatters; } public String getFeasibilityNotes() { return feasibilityNotes; }
    public GoalCategory getCategory() { return category; } public GoalMetric getMetricType() { return metricType; }
    public BigDecimal getBaselineValue() { return baselineValue; } public BigDecimal getTargetValue() { return targetValue; }
    public String getUnit() { return unit; } public LocalDate getStartDate() { return startDate; }
    public LocalDate getTargetDate() { return targetDate; } public GoalStatus getStatus() { return status; }
    public UUID getProjectId() { return projectId; } public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; } public Instant getCompletedAt() { return completedAt; }
    public Instant getArchivedAt() { return archivedAt; } public long getVersion() { return version == null ? 0 : version; }
}
