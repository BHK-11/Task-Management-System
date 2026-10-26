package com.taskos.time.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "recurrence_rules", schema = "task_os")
public class RecurrenceRule {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "source_task_id", nullable = false, updatable = false) private UUID sourceTaskId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private RecurrenceFrequency frequency;
    @Column(name = "interval_count", nullable = false) private int intervalCount;
    @Column(name = "timezone_id", nullable = false, length = 64) private String timezoneId;
    @Column(name = "anchor_date", nullable = false) private LocalDate anchorDate;
    @Column(name = "local_time", nullable = false) private LocalTime localTime;
    @Column(nullable = false, length = 32) private String weekdays;
    @Enumerated(EnumType.STRING) @Column(name = "month_end_policy", nullable = false, length = 16) private MonthEndPolicy monthEndPolicy;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "occurrence_limit") private Integer occurrenceLimit;
    @Column(name = "next_occurrence_date", nullable = false) private LocalDate nextOccurrenceDate;
    @Column(name = "generated_count", nullable = false) private int generatedCount;
    @Column(nullable = false) private boolean paused;
    @Column(nullable = false) private boolean ended;
    @Column(name = "created_at", nullable = false, updatable = false) private java.time.Instant createdAt;
    @Column(name = "updated_at", nullable = false) private java.time.Instant updatedAt;
    @Version private Long version;

    protected RecurrenceRule() { }
    public static RecurrenceRule create(UUID ownerId, UUID sourceTaskId, RecurrenceFrequency frequency, int interval,
            String timezone, LocalDate start, LocalTime time, String weekdays, MonthEndPolicy monthEndPolicy,
            LocalDate end, Integer limit, java.time.Instant now) {
        RecurrenceRule rule = new RecurrenceRule(); rule.id = UUID.randomUUID(); rule.ownerId = ownerId;
        rule.sourceTaskId = sourceTaskId; rule.frequency = frequency; rule.intervalCount = interval;
        rule.timezoneId = timezone; rule.anchorDate = start; rule.localTime = time; rule.weekdays = weekdays;
        rule.monthEndPolicy = monthEndPolicy; rule.endDate = end; rule.occurrenceLimit = limit;
        rule.nextOccurrenceDate = start; rule.createdAt = now; rule.updatedAt = now; return rule;
    }
    public void advance(LocalDate next, java.time.Instant now) { nextOccurrenceDate = next; generatedCount++; updatedAt = now; }
    public void setPaused(boolean paused, java.time.Instant now) { this.paused = paused; updatedAt = now; }
    public void end(java.time.Instant now) { ended = true; updatedAt = now; }
    public boolean finished() { return ended || (endDate != null && nextOccurrenceDate.isAfter(endDate)) || (occurrenceLimit != null && generatedCount >= occurrenceLimit); }
    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getSourceTaskId() { return sourceTaskId; }
    public RecurrenceFrequency getFrequency() { return frequency; }
    public int getIntervalCount() { return intervalCount; }
    public String getTimezoneId() { return timezoneId; }
    public LocalDate getAnchorDate() { return anchorDate; }
    public LocalTime getLocalTime() { return localTime; }
    public String getWeekdays() { return weekdays; }
    public MonthEndPolicy getMonthEndPolicy() { return monthEndPolicy; }
    public LocalDate getEndDate() { return endDate; }
    public Integer getOccurrenceLimit() { return occurrenceLimit; }
    public LocalDate getNextOccurrenceDate() { return nextOccurrenceDate; }
    public int getGeneratedCount() { return generatedCount; }
    public boolean isPaused() { return paused; }
    public boolean isEnded() { return ended; }
    public long getVersion() { return version == null ? 0 : version; }
}
