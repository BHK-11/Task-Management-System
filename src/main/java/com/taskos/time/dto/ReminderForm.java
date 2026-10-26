package com.taskos.time.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

public class ReminderForm {
    private UUID taskId;
    private UUID timeBlockId;
    @NotBlank @Size(max = 160) private String title;
    @NotNull private LocalDateTime scheduledFor;
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public UUID getTimeBlockId() { return timeBlockId; }
    public void setTimeBlockId(UUID timeBlockId) { this.timeBlockId = timeBlockId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public LocalDateTime getScheduledFor() { return scheduledFor; }
    public void setScheduledFor(LocalDateTime scheduledFor) { this.scheduledFor = scheduledFor; }
}
