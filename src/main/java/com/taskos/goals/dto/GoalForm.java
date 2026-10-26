package com.taskos.goals.dto;

import com.taskos.goals.entity.GoalCategory;
import com.taskos.goals.entity.GoalMetric;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class GoalForm {
    @NotBlank @Size(max = 160) private String title;
    @Size(max = 2000) private String description = "";
    @Size(max = 500) private String specificOutcome = "";
    @Size(max = 240) private String measurementDefinition = "";
    @Size(max = 500) private String whyItMatters = "";
    @Size(max = 500) private String feasibilityNotes = "";
    @NotNull private GoalCategory category = GoalCategory.PERSONAL;
    @NotNull private GoalMetric metricType = GoalMetric.NUMERIC_INCREASE;
    @Digits(integer = 12, fraction = 4) @DecimalMin("-999999999999.9999") @DecimalMax("999999999999.9999") private BigDecimal baselineValue = BigDecimal.ZERO;
    @Digits(integer = 12, fraction = 4) @DecimalMin("-999999999999.9999") @DecimalMax("999999999999.9999") private BigDecimal targetValue;
    @Size(max = 40) private String unit = "";
    @NotNull private LocalDate startDate = LocalDate.now();
    private LocalDate targetDate;
    private UUID projectId;
    public String getTitle() { return title; } public void setTitle(String title) { this.title = title; }
    public String getDescription() { return description; } public void setDescription(String description) { this.description = description; }
    public String getSpecificOutcome() { return specificOutcome; } public void setSpecificOutcome(String value) { specificOutcome = value; }
    public String getMeasurementDefinition() { return measurementDefinition; } public void setMeasurementDefinition(String value) { measurementDefinition = value; }
    public String getWhyItMatters() { return whyItMatters; } public void setWhyItMatters(String value) { whyItMatters = value; }
    public String getFeasibilityNotes() { return feasibilityNotes; } public void setFeasibilityNotes(String value) { feasibilityNotes = value; }
    public GoalCategory getCategory() { return category; } public void setCategory(GoalCategory value) { category = value; }
    public GoalMetric getMetricType() { return metricType; } public void setMetricType(GoalMetric value) { metricType = value; }
    public BigDecimal getBaselineValue() { return baselineValue; } public void setBaselineValue(BigDecimal value) { baselineValue = value; }
    public BigDecimal getTargetValue() { return targetValue; } public void setTargetValue(BigDecimal value) { targetValue = value; }
    public String getUnit() { return unit; } public void setUnit(String value) { unit = value; }
    public LocalDate getStartDate() { return startDate; } public void setStartDate(LocalDate value) { startDate = value; }
    public LocalDate getTargetDate() { return targetDate; } public void setTargetDate(LocalDate value) { targetDate = value; }
    public UUID getProjectId() { return projectId; } public void setProjectId(UUID value) { projectId = value; }
}
