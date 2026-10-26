package com.taskos.goals.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

public class OkrForm {
    @NotBlank @Size(max = 160) private String title;
    @Size(max = 1200) private String description = "";
    @NotBlank @Size(max = 100) private String cycleName;
    @NotNull private LocalDate cycleStart;
    @NotNull private LocalDate cycleEnd;
    private UUID linkedGoalId;
    public String getTitle() { return title; } public void setTitle(String v) { title = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public String getCycleName() { return cycleName; } public void setCycleName(String v) { cycleName = v; }
    public LocalDate getCycleStart() { return cycleStart; } public void setCycleStart(LocalDate v) { cycleStart = v; }
    public LocalDate getCycleEnd() { return cycleEnd; } public void setCycleEnd(LocalDate v) { cycleEnd = v; }
    public UUID getLinkedGoalId() { return linkedGoalId; } public void setLinkedGoalId(UUID v) { linkedGoalId = v; }
}
