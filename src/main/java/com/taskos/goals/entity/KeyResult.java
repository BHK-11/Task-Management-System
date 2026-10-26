package com.taskos.goals.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "key_results", schema = "task_os")
public class KeyResult {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "objective_id", nullable = false, updatable = false) private UUID objectiveId;
    @Column(nullable = false, length = 160) private String title;
    @Column(name = "measurement_unit", nullable = false, length = 40) private String measurementUnit;
    @Column(name = "baseline_value", nullable = false, precision = 16, scale = 4) private BigDecimal baselineValue;
    @Column(name = "target_value", nullable = false, precision = 16, scale = 4) private BigDecimal targetValue;
    @Column(nullable = false, length = 12) private String direction;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;
    protected KeyResult() { }
    public static KeyResult create(UUID ownerId, UUID objectiveId, String title, String unit, BigDecimal baseline,
            BigDecimal target, String direction, Instant now) {
        var kr = new KeyResult(); kr.id = UUID.randomUUID(); kr.ownerId = ownerId; kr.objectiveId = objectiveId;
        kr.title = title.strip(); kr.measurementUnit = unit == null ? "" : unit.strip(); kr.baselineValue = baseline;
        kr.targetValue = target; kr.direction = direction; kr.status = "ACTIVE"; kr.createdAt = now; kr.updatedAt = now; return kr;
    }
    public UUID getId() { return id; } public UUID getObjectiveId() { return objectiveId; } public String getTitle() { return title; }
    public String getMeasurementUnit() { return measurementUnit; } public BigDecimal getBaselineValue() { return baselineValue; }
    public BigDecimal getTargetValue() { return targetValue; } public String getDirection() { return direction; } public String getStatus() { return status; }
}
