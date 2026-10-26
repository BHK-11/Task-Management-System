package com.taskos.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class AiPlanForm {
    @NotBlank(message = "Describe what you want the copilot to plan.")
    @Size(max = 20000, message = "Keep the instruction under 20,000 characters.")
    private String instruction;

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
}
