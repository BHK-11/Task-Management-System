package com.taskos.time.dto;

import jakarta.validation.constraints.Size;
import java.util.UUID;

public class TrackingStartForm {
    private UUID taskId;
    @Size(max = 240) private String description = "";
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}
