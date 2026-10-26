package com.taskos.habits.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "habit_check_ins", schema = "task_os")
public class HabitCheckIn {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "habit_id", nullable = false, updatable = false) private UUID habitId;
    @Column(name = "local_date", nullable = false) private LocalDate localDate;
    @Column(precision = 16, scale = 4) private BigDecimal quantity;
    @Column(nullable = false) private boolean performed;
    @Column(nullable = false, length = 600) private String notes;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;
    protected HabitCheckIn() { }
    public static HabitCheckIn record(UUID ownerId, UUID habitId, LocalDate date, BigDecimal quantity, boolean performed, String notes, Instant now) {
        var item = new HabitCheckIn(); item.id = UUID.randomUUID(); item.ownerId = ownerId; item.habitId = habitId;
        item.localDate = date; item.quantity = quantity; item.performed = performed; item.notes = notes == null ? "" : notes.strip();
        item.recordedAt = now; item.updatedAt = now; return item;
    }
    public void update(BigDecimal quantity, boolean performed, String notes, Instant now) { this.quantity = quantity; this.performed = performed; this.notes = notes == null ? "" : notes.strip(); updatedAt = now; }
    public UUID getId() { return id; } public LocalDate getLocalDate() { return localDate; } public BigDecimal getQuantity() { return quantity; }
    public boolean isPerformed() { return performed; } public String getNotes() { return notes; } public Instant getRecordedAt() { return recordedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
