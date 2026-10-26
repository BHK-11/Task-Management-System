package com.taskos.task.dto;

import com.taskos.task.entity.TaskTagColor;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class TagForm {
    @NotBlank(message = "Enter a tag name.")
    @Size(max = 32, message = "Use 32 characters or fewer.")
    private String name;

    @NotNull
    private TaskTagColor color = TaskTagColor.SLATE;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public TaskTagColor getColor() { return color; }
    public void setColor(TaskTagColor color) { this.color = color; }
}
