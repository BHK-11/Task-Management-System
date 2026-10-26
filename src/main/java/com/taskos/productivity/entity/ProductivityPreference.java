package com.taskos.productivity.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "productivity_preferences", schema = "task_os")
public class ProductivityPreference {
    @Id @Column(name = "owner_id", nullable = false, updatable = false) private UUID id;
    @Enumerated(EnumType.STRING) @Column(name = "primary_method", nullable = false, length = 16)
    private ProductivityMethod primaryMethod = ProductivityMethod.DAILY_PLAN;
    @Enumerated(EnumType.STRING) @Column(name = "default_plan_mode", nullable = false, length = 16)
    private DailyPlanMode defaultPlanMode = DailyPlanMode.IVY_LEE;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version @Column(nullable = false) private Long version;

    protected ProductivityPreference() { }
    public static ProductivityPreference create(UUID ownerId, Instant now) {
        ProductivityPreference preference = new ProductivityPreference();
        preference.id = ownerId; preference.updatedAt = now;
        return preference;
    }
    public void update(ProductivityMethod method, DailyPlanMode mode, Instant now) {
        primaryMethod = method; defaultPlanMode = mode; updatedAt = now;
    }
    public UUID getId() { return id; }
    public ProductivityMethod getPrimaryMethod() { return primaryMethod; }
    public DailyPlanMode getDefaultPlanMode() { return defaultPlanMode; }
    public Instant getUpdatedAt() { return updatedAt; }
}
