package com.taskos.agile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "sprints", schema = "task_os")
public class Sprint {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 500) private String goal;
    @Column(name = "planning_notes", nullable = false, length = 3000) private String planningNotes = "";
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date", nullable = false) private LocalDate endDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private SprintStatus status;
    @Column(name = "capacity_points") private Integer capacityPoints;
    @Column(name = "baseline_points", nullable = false) private int baselinePoints;
    @Column(name = "baseline_item_count", nullable = false) private int baselineItemCount;
    @Column(name = "delivered_points", nullable = false) private int deliveredPoints;
    @Column(name = "final_scope_points", nullable = false) private int finalScopePoints;
    @Column(name = "final_remaining_points", nullable = false) private int finalRemainingPoints;
    @Column(name = "completed_item_count", nullable = false) private int completedItemCount;
    @Column(name = "incomplete_item_count", nullable = false) private int incompleteItemCount;
    @Column(name = "started_at") private Instant startedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "cancelled_at") private Instant cancelledAt;
    @Version private Long version;

    protected Sprint() { }
    public static Sprint planned(UUID projectId, String name, String goal, String notes, LocalDate start,
            LocalDate end, Integer capacity) {
        Sprint sprint = new Sprint(); sprint.id = UUID.randomUUID(); sprint.projectId = projectId;
        sprint.name = name.trim(); sprint.goal = goal.trim(); sprint.planningNotes = notes == null ? "" : notes.trim();
        sprint.startDate = start; sprint.endDate = end; sprint.capacityPoints = capacity;
        sprint.status = SprintStatus.PLANNED; return sprint;
    }
    public void update(String name, String goal, String notes, LocalDate start, LocalDate end, Integer capacity) {
        if (status != SprintStatus.PLANNED && status != SprintStatus.ACTIVE) throw new IllegalStateException("Only a planned or active sprint can be edited.");
        this.name = name.trim(); this.goal = goal.trim(); this.planningNotes = notes == null ? "" : notes.trim();
        this.startDate = start; this.endDate = end; this.capacityPoints = capacity;
    }
    public void start(Instant now, int points, int count) {
        if (status != SprintStatus.PLANNED) throw new IllegalStateException("Only a planned sprint can start.");
        status = SprintStatus.ACTIVE; startedAt = now; baselinePoints = points; baselineItemCount = count;
    }
    public void complete(Instant now, int points, int finalScopePoints, int remainingPoints, int completedItems, int incompleteItems) {
        if (status != SprintStatus.ACTIVE) throw new IllegalStateException("Only an active sprint can complete.");
        status = SprintStatus.COMPLETED; completedAt = now; deliveredPoints = points; this.finalScopePoints = finalScopePoints;
        finalRemainingPoints = remainingPoints; completedItemCount = completedItems; incompleteItemCount = incompleteItems;
    }
    public void cancel(Instant now) {
        if (status != SprintStatus.PLANNED && status != SprintStatus.ACTIVE) throw new IllegalStateException("This sprint cannot be cancelled.");
        status = SprintStatus.CANCELLED; cancelledAt = now;
    }
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getName() { return name; }
    public String getGoal() { return goal; }
    public String getPlanningNotes() { return planningNotes; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getEndDate() { return endDate; }
    public SprintStatus getStatus() { return status; }
    public Integer getCapacityPoints() { return capacityPoints; }
    public int getBaselinePoints() { return baselinePoints; }
    public int getBaselineItemCount() { return baselineItemCount; }
    public int getDeliveredPoints() { return deliveredPoints; }
    public int getFinalScopePoints() { return finalScopePoints; }
    public int getFinalRemainingPoints() { return finalRemainingPoints; }
    public int getCompletedItemCount() { return completedItemCount; }
    public int getIncompleteItemCount() { return incompleteItemCount; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getCancelledAt() { return cancelledAt; }
    public long getVersion() { return version == null ? 0 : version; }
}
