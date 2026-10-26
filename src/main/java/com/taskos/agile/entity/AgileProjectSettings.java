package com.taskos.agile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.UUID;

@Entity
@Table(name = "agile_project_settings", schema = "task_os")
public class AgileProjectSettings {
    @Id @Column(name = "project_id") private UUID projectId;
    @Column(nullable = false) private boolean enabled;
    @Column(name = "definition_of_done", nullable = false, length = 4000) private String definitionOfDone = "";
    @Version private Long version;

    protected AgileProjectSettings() { }
    public static AgileProjectSettings create(UUID projectId) {
        AgileProjectSettings settings = new AgileProjectSettings(); settings.projectId = projectId; return settings;
    }
    public void configure(boolean enabled, String definitionOfDone) {
        this.enabled = enabled; this.definitionOfDone = definitionOfDone == null ? "" : definitionOfDone.trim();
    }
    public UUID getProjectId() { return projectId; }
    public boolean isEnabled() { return enabled; }
    public String getDefinitionOfDone() { return definitionOfDone; }
}
