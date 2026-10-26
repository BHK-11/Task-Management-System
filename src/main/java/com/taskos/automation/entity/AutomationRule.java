package com.taskos.automation.entity;

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
@Table(name = "automation_rules", schema = "task_os")
public class AutomationRule {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 500) private String description;
    @Enumerated(EnumType.STRING) @Column(name = "scope_type", nullable = false, length = 16) private AutomationScopeType scopeType;
    @Column(name = "scope_id", nullable = false) private UUID scopeId;
    @Column(name = "created_by_id", nullable = false, updatable = false) private UUID createdById;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private AutomationRuleStatus status;
    @Column(name = "current_version_id") private UUID currentVersionId;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "enabled_at") private Instant enabledAt;
    @Column(name = "disabled_at") private Instant disabledAt;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "last_execution_at") private Instant lastExecutionAt;
    @Version private Long version;

    protected AutomationRule() { }

    public static AutomationRule create(String name, String description, AutomationScopeType scopeType,
            UUID scopeId, UUID createdById, Instant now) {
        AutomationRule rule = new AutomationRule();
        rule.id = UUID.randomUUID(); rule.name = name; rule.description = description == null ? "" : description;
        rule.scopeType = scopeType; rule.scopeId = scopeId; rule.createdById = createdById;
        rule.status = AutomationRuleStatus.DRAFT; rule.createdAt = now; rule.updatedAt = now;
        return rule;
    }

    public void publish(UUID versionId, String name, String description, Instant now) {
        if (status == AutomationRuleStatus.ARCHIVED) throw new IllegalStateException("Archived rules cannot be edited.");
        currentVersionId = versionId; this.name = name; this.description = description == null ? "" : description;
        updatedAt = now;
    }
    public void enable(Instant now) {
        if (status == AutomationRuleStatus.ARCHIVED || currentVersionId == null)
            throw new IllegalStateException("Publish a valid rule version before enabling it.");
        status = AutomationRuleStatus.ENABLED; enabledAt = now; disabledAt = null; updatedAt = now;
    }
    public void disable(Instant now) {
        if (status == AutomationRuleStatus.ARCHIVED) throw new IllegalStateException("Archived rules cannot be changed.");
        status = AutomationRuleStatus.DISABLED; disabledAt = now; updatedAt = now;
    }
    public void archive(Instant now) { status = AutomationRuleStatus.ARCHIVED; archivedAt = now; updatedAt = now; }
    public void recordExecution(Instant now) { lastExecutionAt = now; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public AutomationScopeType getScopeType() { return scopeType; }
    public UUID getScopeId() { return scopeId; }
    public UUID getCreatedById() { return createdById; }
    public AutomationRuleStatus getStatus() { return status; }
    public UUID getCurrentVersionId() { return currentVersionId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getEnabledAt() { return enabledAt; }
    public Instant getDisabledAt() { return disabledAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public Instant getLastExecutionAt() { return lastExecutionAt; }
    public Long getVersion() { return version; }
}
