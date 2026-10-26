package com.taskos.automation.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "automation_rule_versions", schema = "task_os")
public class AutomationRuleVersion {
    @Id private UUID id;
    @Column(name = "rule_id", nullable = false, updatable = false) private UUID ruleId;
    @Column(name = "version_number", nullable = false, updatable = false) private int versionNumber;
    @Column(name = "schema_version", nullable = false, updatable = false) private int schemaVersion;
    @Column(name = "definition_json", nullable = false, length = 12000, updatable = false) private String definitionJson;
    @Column(name = "created_by_id", nullable = false, updatable = false) private UUID createdById;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;

    protected AutomationRuleVersion() { }
    public static AutomationRuleVersion create(UUID ruleId, int versionNumber, int schemaVersion,
            String definitionJson, UUID createdById, Instant now) {
        AutomationRuleVersion version = new AutomationRuleVersion(); version.id = UUID.randomUUID();
        version.ruleId = ruleId; version.versionNumber = versionNumber; version.schemaVersion = schemaVersion;
        version.definitionJson = definitionJson; version.createdById = createdById; version.createdAt = now;
        return version;
    }
    public UUID getId() { return id; }
    public UUID getRuleId() { return ruleId; }
    public int getVersionNumber() { return versionNumber; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getDefinitionJson() { return definitionJson; }
    public UUID getCreatedById() { return createdById; }
    public Instant getCreatedAt() { return createdAt; }
}
