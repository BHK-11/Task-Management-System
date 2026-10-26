package com.taskos.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "goal_task_links", schema = "task_os")
public class GoalTaskLink {
    @Id private UUID id;
    @Column(name = "goal_id", nullable = false) private UUID goalId;
    @Column(name = "task_id", nullable = false) private UUID taskId;
    @Column(name = "owner_id", nullable = false) private UUID ownerId;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    protected GoalTaskLink() { }
    public GoalTaskLink(UUID ownerId, UUID goalId, UUID taskId, Instant now) { this.id = UUID.randomUUID(); this.ownerId = ownerId; this.goalId = goalId; this.taskId = taskId; this.createdAt = now; }
    public UUID getGoalId() { return goalId; }
    public UUID getTaskId() { return taskId; }
}
