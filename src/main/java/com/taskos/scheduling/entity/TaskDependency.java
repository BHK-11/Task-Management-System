package com.taskos.scheduling.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "task_dependencies", schema = "task_os")
public class TaskDependency {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(name = "predecessor_task_id", nullable = false, updatable = false) private UUID predecessorTaskId;
    @Column(name = "successor_task_id", nullable = false, updatable = false) private UUID successorTaskId;
    @Enumerated(EnumType.STRING) @Column(name = "dependency_type", nullable = false, length = 3, updatable = false)
    private TaskDependencyType type;
    @Column(name = "lag_days", nullable = false, updatable = false) private int lagDays;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected TaskDependency() { }

    public static TaskDependency create(UUID ownerId, UUID projectId, UUID predecessorTaskId,
            UUID successorTaskId, TaskDependencyType type, int lagDays, Instant now) {
        TaskDependency dependency = new TaskDependency();
        dependency.id = UUID.randomUUID(); dependency.ownerId = ownerId; dependency.projectId = projectId;
        dependency.predecessorTaskId = predecessorTaskId; dependency.successorTaskId = successorTaskId;
        dependency.type = type; dependency.lagDays = lagDays; dependency.createdAt = now;
        return dependency;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getProjectId() { return projectId; }
    public UUID getPredecessorTaskId() { return predecessorTaskId; }
    public UUID getSuccessorTaskId() { return successorTaskId; }
    public TaskDependencyType getType() { return type; }
    public int getLagDays() { return lagDays; }
    public Instant getCreatedAt() { return createdAt; }
}
