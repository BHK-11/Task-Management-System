package com.taskos.project.entity;

import com.taskos.task.entity.Task;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "project_task_placements", schema = "task_os")
public class ProjectTaskPlacement {
    @Id @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "project_id", nullable = false) private UUID projectId;
    @Column(name = "column_id", nullable = false, insertable = false, updatable = false) private UUID columnId;
    @Column(name = "milestone_id") private UUID milestoneId;
    @Column(nullable = false) private int position;
    @Version private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", insertable = false, updatable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "column_id", nullable = false)
    private BoardColumn boardColumn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "milestone_id", insertable = false, updatable = false)
    private ProjectMilestone milestone;

    protected ProjectTaskPlacement() { }

    public static ProjectTaskPlacement create(UUID ownerId, UUID projectId, Task task,
            BoardColumn column, int position) {
        ProjectTaskPlacement placement = new ProjectTaskPlacement();
        placement.taskId = task.getId(); placement.ownerId = ownerId; placement.projectId = projectId;
        placement.columnId = column.getId(); placement.position = position;
        placement.task = task; placement.boardColumn = column;
        return placement;
    }

    public void move(UUID projectId, BoardColumn column, int position) {
        this.projectId = projectId; this.columnId = column.getId(); this.boardColumn = column; this.position = position;
    }

    public void setPosition(int position) { this.position = position; }
    public void setMilestone(ProjectMilestone milestone) { this.milestoneId = milestone == null ? null : milestone.getId(); this.milestone = milestone; }
    public void setMilestoneId(UUID milestoneId) { this.milestoneId = milestoneId; }
    public UUID getTaskId() { return taskId; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getProjectId() { return projectId; }
    public UUID getColumnId() { return columnId; }
    public UUID getMilestoneId() { return milestoneId; }
    public int getPosition() { return position; }
    public Task getTask() { return task; }
    public BoardColumn getColumn() { return boardColumn; }
    public ProjectMilestone getMilestone() { return milestone; }
}
