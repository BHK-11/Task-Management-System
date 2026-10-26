package com.taskos.habits.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "habit_schedules", schema = "task_os")
public class HabitSchedule {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "habit_id", nullable = false, updatable = false) private UUID habitId;
    @Column(name = "effective_from", nullable = false) private LocalDate effectiveFrom;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private HabitFrequency frequency;
    @Column(name = "selected_weekdays", nullable = false, length = 20) private String selectedWeekdays;
    @Column(name = "target_count", nullable = false) private int targetCount;
    @Column(name = "target_quantity", precision = 16, scale = 4) private BigDecimal targetQuantity;
    @Column(nullable = false, length = 40) private String unit;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected HabitSchedule() { }
    public static HabitSchedule create(UUID ownerId, UUID habitId, LocalDate effective, HabitFrequency frequency,
            String weekdays, int targetCount, BigDecimal targetQuantity, String unit, Instant now) {
        var item = new HabitSchedule(); item.id = UUID.randomUUID(); item.ownerId = ownerId; item.habitId = habitId;
        item.effectiveFrom = effective; item.frequency = frequency; item.selectedWeekdays = weekdays == null ? "" : weekdays;
        item.targetCount = targetCount; item.targetQuantity = targetQuantity; item.unit = unit == null ? "" : unit.strip(); item.createdAt = now; return item;
    }
    public UUID getId() { return id; } public LocalDate getEffectiveFrom() { return effectiveFrom; }
    public HabitFrequency getFrequency() { return frequency; } public String getSelectedWeekdays() { return selectedWeekdays; }
    public int getTargetCount() { return targetCount; } public BigDecimal getTargetQuantity() { return targetQuantity; } public String getUnit() { return unit; }
}
