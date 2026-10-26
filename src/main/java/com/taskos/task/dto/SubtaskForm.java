package com.taskos.task.dto;

import com.taskos.task.entity.TaskPriority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class SubtaskForm {
    @NotBlank(message = "Enter a subtask title.")
    @Size(max = 160, message = "Use 160 characters or fewer.")
    private String title;

    @NotNull
    private TaskPriority priority = TaskPriority.MEDIUM;

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public TaskPriority getPriority() { return priority; }
    public void setPriority(TaskPriority priority) { this.priority = priority; }
}
