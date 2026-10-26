package com.taskos.project.dto;

import com.taskos.project.entity.ProjectStatus;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

public class ProjectForm {
    @NotBlank(message = "Enter a project name.")
    @Size(max = 160, message = "Use 160 characters or fewer.")
    private String name;
    @Size(max = 2000, message = "Use 2,000 characters or fewer.")
    private String description = "";
    @NotNull(message = "Choose a project status.")
    private ProjectStatus status = ProjectStatus.PLANNED;
    private LocalDate startDate;
    private LocalDate targetEndDate;
    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Choose a six-digit hex color.")
    private String color = "#4f46e5";
    private long version;

    @AssertTrue(message = "The target date must be on or after the start date.")
    public boolean isDateRangeValid() {
        return startDate == null || targetEndDate == null || !targetEndDate.isBefore(startDate);
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public ProjectStatus getStatus() { return status; }
    public void setStatus(ProjectStatus status) { this.status = status; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getTargetEndDate() { return targetEndDate; }
    public void setTargetEndDate(LocalDate targetEndDate) { this.targetEndDate = targetEndDate; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }
}
