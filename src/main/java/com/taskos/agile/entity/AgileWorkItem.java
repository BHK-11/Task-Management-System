package com.taskos.agile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "agile_work_items", schema = "task_os")
public class AgileWorkItem {
    public static final int[] STORY_POINT_SCALE = {0, 1, 2, 3, 5, 8, 13, 20};
    @Id @Column(name = "task_id") private UUID taskId;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(name = "epic_id") private UUID epicId;
    @Enumerated(EnumType.STRING) @Column(name = "work_item_type", nullable = false, length = 16) private AgileWorkItemType type;
    @Column(name = "story_points") private Integer storyPoints;
    @Column(name = "backlog_position", nullable = false) private int backlogPosition;
    @Column(name = "definition_of_done", nullable = false, length = 2000) private String definitionOfDone = "";
    @Version private Long version;

    protected AgileWorkItem() { }
    public static AgileWorkItem create(UUID taskId, UUID projectId, int position) {
        AgileWorkItem item = new AgileWorkItem(); item.taskId = taskId; item.projectId = projectId;
        item.type = AgileWorkItemType.TASK; item.backlogPosition = position; return item;
    }
    public void update(AgileWorkItemType type, Integer points, UUID epicId, String definitionOfDone) {
        this.type = type; this.storyPoints = points; this.epicId = epicId;
        this.definitionOfDone = definitionOfDone == null ? "" : definitionOfDone.trim();
    }
    public void moveTo(int position) { backlogPosition = position; }
    public UUID getTaskId() { return taskId; }
    public UUID getProjectId() { return projectId; }
    public UUID getEpicId() { return epicId; }
    public AgileWorkItemType getType() { return type; }
    public Integer getStoryPoints() { return storyPoints; }
    public int getBacklogPosition() { return backlogPosition; }
    public String getDefinitionOfDone() { return definitionOfDone; }
}
