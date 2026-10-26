package com.taskos.time.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

public class ManualTimeEntryForm {
    private UUID taskId;
    @Size(max = 240) private String description = "";
    @NotNull private LocalDateTime startAt;
    @NotNull private LocalDateTime endAt;
    private long version;
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
