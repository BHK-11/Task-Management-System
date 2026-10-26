package com.taskos.task.dto;

import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BulkTaskActionForm {
    @NotEmpty(message = "Select at least one task.")
    @Size(max = 50, message = "Update no more than 50 tasks at a time.")
    private List<UUID> taskIds = new ArrayList<>();

    @NotNull(message = "Choose an action.")
    private BulkTaskAction action;

    private TaskStatus status;
    private TaskPriority priority;
    private UUID tagId;

    public List<UUID> getTaskIds() { return taskIds; }
    public void setTaskIds(List<UUID> taskIds) { this.taskIds = taskIds == null ? new ArrayList<>() : taskIds; }
    public BulkTaskAction getAction() { return action; }
    public void setAction(BulkTaskAction action) { this.action = action; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public UUID getTagId() { return tagId; }
    public void setTagId(UUID tagId) { this.tagId = tagId; }
}
