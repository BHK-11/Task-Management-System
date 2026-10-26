package com.taskos.task.dto;

import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class TaskForm {
    @NotBlank(message = "Enter a task title.")
    @Size(max = 160, message = "Use 160 characters or fewer.")
    private String title;

    @Size(max = 6000, message = "Use 6,000 characters or fewer.")
    private String description = "";

    @NotNull(message = "Choose a status.")
    private TaskStatus status = TaskStatus.TODO;

    @NotNull(message = "Choose a priority.")
    private TaskPriority priority = TaskPriority.MEDIUM;

    private LocalDate startDate;
    private LocalDate dueDate;

    @Size(max = 10, message = "A task can have up to 10 tags.")
    private List<UUID> tagIds = new ArrayList<>();

    private long version;

    @AssertTrue(message = "The due date must be on or after the start date.")
    public boolean isDateRangeValid() {
        return startDate == null || dueDate == null || !dueDate.isBefore(startDate);
    }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public TaskStatus getStatus() { return status; }
    public void setStatus(TaskStatus status) { this.status = status; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public List<UUID> getTagIds() { return tagIds; }
    public void setTagIds(List<UUID> tagIds) { this.tagIds = tagIds == null ? new ArrayList<>() : tagIds; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
