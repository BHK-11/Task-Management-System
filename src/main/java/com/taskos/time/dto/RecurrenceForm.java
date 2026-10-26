package com.taskos.time.dto;

import com.taskos.time.entity.MonthEndPolicy;
import com.taskos.time.entity.RecurrenceFrequency;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

public class RecurrenceForm {
    @NotNull private UUID sourceTaskId;
    @NotNull private RecurrenceFrequency frequency = RecurrenceFrequency.WEEKLY;
    @Min(1) @Max(365) private int intervalCount = 1;
    @NotNull private LocalDate startDate;
    private LocalDate endDate;
    @Min(1) @Max(10000) private Integer occurrenceLimit;
    @NotNull private LocalTime localTime = LocalTime.of(9, 0);
    private String weekdays = "1,2,3,4,5";
    @NotNull private MonthEndPolicy monthEndPolicy = MonthEndPolicy.LAST_DAY;
    public UUID getSourceTaskId() { return sourceTaskId; }
    public void setSourceTaskId(UUID sourceTaskId) { this.sourceTaskId = sourceTaskId; }
    public RecurrenceFrequency getFrequency() { return frequency; }
    public void setFrequency(RecurrenceFrequency frequency) { this.frequency = frequency; }
    public int getIntervalCount() { return intervalCount; }
    public void setIntervalCount(int intervalCount) { this.intervalCount = intervalCount; }
    public LocalDate getStartDate() { return startDate; }
    public void setStartDate(LocalDate startDate) { this.startDate = startDate; }
    public LocalDate getEndDate() { return endDate; }
    public void setEndDate(LocalDate endDate) { this.endDate = endDate; }
    public Integer getOccurrenceLimit() { return occurrenceLimit; }
    public void setOccurrenceLimit(Integer occurrenceLimit) { this.occurrenceLimit = occurrenceLimit; }
    public LocalTime getLocalTime() { return localTime; }
    public void setLocalTime(LocalTime localTime) { this.localTime = localTime; }
    public String getWeekdays() { return weekdays; }
    public void setWeekdays(String weekdays) { this.weekdays = weekdays; }
    public MonthEndPolicy getMonthEndPolicy() { return monthEndPolicy; }
    public void setMonthEndPolicy(MonthEndPolicy monthEndPolicy) { this.monthEndPolicy = monthEndPolicy; }
}
