package com.taskos.habits.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "habit_pauses", schema = "task_os")
public class HabitPause {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "habit_id", nullable = false, updatable = false) private UUID habitId;
    @Column(name = "start_date", nullable = false) private LocalDate startDate;
    @Column(name = "end_date") private LocalDate endDate;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    protected HabitPause() { }
    public static HabitPause start(UUID ownerId, UUID habitId, LocalDate date, Instant now) {
        var pause = new HabitPause(); pause.id = UUID.randomUUID(); pause.ownerId = ownerId; pause.habitId = habitId; pause.startDate = date; pause.createdAt = now; return pause;
    }
    public void resume(LocalDate date) { if (date.isBefore(startDate)) throw new IllegalArgumentException("Resume date cannot be before the pause started."); endDate = date; }
    public LocalDate getStartDate() { return startDate; } public LocalDate getEndDate() { return endDate; }
}
