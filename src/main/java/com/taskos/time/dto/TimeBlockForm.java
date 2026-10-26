package com.taskos.time.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDateTime;
import java.util.UUID;

public class TimeBlockForm {
    @NotBlank @Size(max = 160) private String title;
    private UUID taskId;
    @NotNull private LocalDateTime startAt;
    @NotNull private LocalDateTime endAt;
    @Size(max = 1200) private String notes = "";
    private long version;
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public UUID getTaskId() { return taskId; }
    public void setTaskId(UUID taskId) { this.taskId = taskId; }
    public LocalDateTime getStartAt() { return startAt; }
    public void setStartAt(LocalDateTime startAt) { this.startAt = startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public void setEndAt(LocalDateTime endAt) { this.endAt = endAt; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
