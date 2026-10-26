package com.taskos.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "key_result_measurements", schema = "task_os")
public class KeyResultMeasurement {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "key_result_id", nullable = false, updatable = false) private UUID keyResultId;
    @Column(name = "recorded_value", nullable = false, precision = 16, scale = 4) private BigDecimal recordedValue;
    @Column(name = "measurement_date", nullable = false) private LocalDate measurementDate;
    @Column(name = "recorded_at", nullable = false, updatable = false) private Instant recordedAt;
    @Column(nullable = false, length = 1000) private String notes;
    protected KeyResultMeasurement() { }
    public static KeyResultMeasurement record(UUID ownerId, UUID resultId, BigDecimal value, LocalDate date, String notes, Instant now) {
        var m = new KeyResultMeasurement(); m.id = UUID.randomUUID(); m.ownerId = ownerId; m.keyResultId = resultId;
        m.recordedValue = value; m.measurementDate = date; m.notes = notes == null ? "" : notes.strip(); m.recordedAt = now; return m;
    }
    public UUID getId() { return id; } public BigDecimal getRecordedValue() { return recordedValue; }
    public LocalDate getMeasurementDate() { return measurementDate; } public Instant getRecordedAt() { return recordedAt; }
    public String getNotes() { return notes; }
}
