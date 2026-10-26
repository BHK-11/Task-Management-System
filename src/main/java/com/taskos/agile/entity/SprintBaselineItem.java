package com.taskos.agile.entity;

import com.taskos.task.entity.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "sprint_baseline_items", schema = "task_os")
public class SprintBaselineItem {
    @Id private UUID id;
    @Column(name = "sprint_id", nullable = false, updatable = false) private UUID sprintId;
    @Column(name = "task_id", nullable = false, updatable = false) private UUID taskId;
    @Column(nullable = false, length = 160) private String title;
    @Column(name = "story_points") private Integer storyPoints;
    @Enumerated(EnumType.STRING) @Column(name = "task_status", nullable = false, length = 16) private TaskStatus taskStatus;

    protected SprintBaselineItem() { }
    public static SprintBaselineItem capture(UUID sprintId, UUID taskId, String title, Integer points, TaskStatus status) {
        SprintBaselineItem item = new SprintBaselineItem(); item.id = UUID.randomUUID(); item.sprintId = sprintId;
        item.taskId = taskId; item.title = title; item.storyPoints = points; item.taskStatus = status; return item;
    }
    public UUID getTaskId() { return taskId; }
    public String getTitle() { return title; }
    public Integer getStoryPoints() { return storyPoints; }
    public TaskStatus getTaskStatus() { return taskStatus; }
}
