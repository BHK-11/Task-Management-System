package com.taskos.automation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "automation_schedules", schema = "task_os")
public class AutomationSchedule {
    @Id private UUID id;
    @Column(name = "rule_id", nullable = false, unique = true, updatable = false) private UUID ruleId;
    @Column(name = "rule_version_id", nullable = false, updatable = false) private UUID ruleVersionId;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AutomationScheduleFrequency frequency;
    @Enumerated(EnumType.STRING) @Column(name = "day_of_week", length = 12) private DayOfWeek dayOfWeek;
    @Column(name = "local_time", nullable = false) private LocalTime localTime;
    @Column(nullable = false, length = 80) private String timezone;
    @Enumerated(EnumType.STRING) @Column(name = "misfire_policy", nullable = false, length = 32) private AutomationMisfirePolicy misfirePolicy;
    @Column(name = "next_fire_at", nullable = false) private Instant nextFireAt;
    @Column(name = "last_fire_at") private Instant lastFireAt;
    @Column(nullable = false) private boolean active;
    @Version private Long version;

    protected AutomationSchedule() { }
    public static AutomationSchedule create(UUID ruleId, UUID versionId, AutomationScheduleFrequency frequency,
            DayOfWeek dayOfWeek, LocalTime localTime, String timezone, AutomationMisfirePolicy policy, Instant nextFireAt) {
        AutomationSchedule schedule = new AutomationSchedule(); schedule.id = UUID.randomUUID(); schedule.ruleId = ruleId;
        schedule.ruleVersionId = versionId; schedule.frequency = frequency; schedule.dayOfWeek = dayOfWeek;
        schedule.localTime = localTime; schedule.timezone = timezone; schedule.misfirePolicy = policy;
        schedule.nextFireAt = nextFireAt; schedule.active = true; return schedule;
    }
    public void fire(Instant firedAt, Instant nextFireAt) { lastFireAt = firedAt; this.nextFireAt = nextFireAt; }
    public void updateVersion(UUID versionId, Instant nextFireAt) { ruleVersionId = versionId; this.nextFireAt = nextFireAt; active = true; }
    public void configure(UUID versionId, AutomationScheduleFrequency frequency, DayOfWeek dayOfWeek,
            LocalTime localTime, String timezone, AutomationMisfirePolicy policy, Instant nextFireAt, boolean active) {
        ruleVersionId = versionId; this.frequency = frequency; this.dayOfWeek = dayOfWeek;
        this.localTime = localTime; this.timezone = timezone; misfirePolicy = policy;
        this.nextFireAt = nextFireAt; this.active = active;
    }
    public void deactivate() { active = false; }
    public UUID getId() { return id; }
    public UUID getRuleId() { return ruleId; }
    public UUID getRuleVersionId() { return ruleVersionId; }
    public AutomationScheduleFrequency getFrequency() { return frequency; }
    public DayOfWeek getDayOfWeek() { return dayOfWeek; }
    public LocalTime getLocalTime() { return localTime; }
    public String getTimezone() { return timezone; }
    public AutomationMisfirePolicy getMisfirePolicy() { return misfirePolicy; }
    public Instant getNextFireAt() { return nextFireAt; }
    public Instant getLastFireAt() { return lastFireAt; }
    public boolean isActive() { return active; }
}
