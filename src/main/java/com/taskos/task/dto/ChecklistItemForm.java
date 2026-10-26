package com.taskos.task.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class ChecklistItemForm {
    @NotBlank(message = "Enter a checklist item.")
    @Size(max = 240, message = "Use 240 characters or fewer.")
    private String text;

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}
