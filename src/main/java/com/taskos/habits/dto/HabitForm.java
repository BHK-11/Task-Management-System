package com.taskos.habits.dto;

import com.taskos.habits.entity.HabitFrequency;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public class HabitForm {
    @NotBlank @Size(max = 120) private String name;
    @Size(max = 1000) private String description = "";
    @NotNull private HabitFrequency frequency = HabitFrequency.DAILY;
    @Size(max = 20) private String selectedWeekdays = "";
    @Min(1) @Max(31) private int targetCount = 1;
    @Digits(integer = 12, fraction = 4) @DecimalMin("0.0001") private BigDecimal targetQuantity;
    @Size(max = 40) private String unit = "";
    @NotNull private LocalDate startDate;
    private LocalDate effectiveDate;
    @NotBlank @Size(max = 64) private String timezone = "UTC";
    private UUID linkedGoalId;
    private UUID linkedTaskId;
    public String getName() { return name; } public void setName(String v) { name = v; }
    public String getDescription() { return description; } public void setDescription(String v) { description = v; }
    public HabitFrequency getFrequency() { return frequency; } public void setFrequency(HabitFrequency v) { frequency = v; }
    public String getSelectedWeekdays() { return selectedWeekdays; } public void setSelectedWeekdays(String v) { selectedWeekdays = v; }
    public int getTargetCount() { return targetCount; } public void setTargetCount(int v) { targetCount = v; }
    public BigDecimal getTargetQuantity() { return targetQuantity; } public void setTargetQuantity(BigDecimal v) { targetQuantity = v; }
    public String getUnit() { return unit; } public void setUnit(String v) { unit = v; }
    public LocalDate getStartDate() { return startDate; } public void setStartDate(LocalDate v) { startDate = v; }
    public LocalDate getEffectiveDate() { return effectiveDate; } public void setEffectiveDate(LocalDate v) { effectiveDate = v; }
    public String getTimezone() { return timezone; } public void setTimezone(String v) { timezone = v; }
    public UUID getLinkedGoalId() { return linkedGoalId; } public void setLinkedGoalId(UUID v) { linkedGoalId = v; }
    public UUID getLinkedTaskId() { return linkedTaskId; } public void setLinkedTaskId(UUID v) { linkedTaskId = v; }
}
