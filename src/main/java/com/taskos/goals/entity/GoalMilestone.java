package com.taskos.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "goal_milestones", schema = "task_os")
public class GoalMilestone {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "goal_id", nullable = false, updatable = false) private UUID goalId;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 1000) private String description;
    @Column(name = "target_date") private LocalDate targetDate;
    @Column(nullable = false, length = 16) private String status;
    @Column(nullable = false) private int position;
    @Column(name = "completed_at") private Instant completedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    protected GoalMilestone() { }
    public static GoalMilestone create(UUID ownerId, UUID goalId, String title, String description, LocalDate targetDate, int position, Instant now) {
        var item = new GoalMilestone(); item.id = UUID.randomUUID(); item.ownerId = ownerId; item.goalId = goalId;
        item.title = title.strip(); item.description = description == null ? "" : description.strip(); item.targetDate = targetDate;
        item.position = position; item.status = "PLANNED"; item.createdAt = now; item.updatedAt = now; return item;
    }
    public void toggle(Instant now) { if ("COMPLETED".equals(status)) { status = "PLANNED"; completedAt = null; } else { status = "COMPLETED"; completedAt = now; } updatedAt = now; }
    public void update(String title, String description, LocalDate targetDate, Instant now) {
        this.title = title.strip(); this.description = description == null ? "" : description.strip(); this.targetDate = targetDate; updatedAt = now;
    }
    public void reorder(int position, Instant now) { this.position = position; updatedAt = now; }
    public UUID getId() { return id; } public UUID getGoalId() { return goalId; } public String getTitle() { return title; }
    public String getDescription() { return description; } public LocalDate getTargetDate() { return targetDate; }
    public String getStatus() { return status; } public int getPosition() { return position; } public Instant getCompletedAt() { return completedAt; }
}
