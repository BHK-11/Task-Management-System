package com.taskos.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AiMemoryForm {
    @NotBlank
    @Size(max = 2000)
    private String content;

    @NotBlank
    private String category = "PREFERENCE";

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
