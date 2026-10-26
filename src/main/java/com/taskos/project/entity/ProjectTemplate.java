package com.taskos.project.entity;

import com.taskos.user.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "project_templates", schema = "task_os")
public class ProjectTemplate {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 1200) private String description = "";
    @Column(name = "schema_version", nullable = false) private int schemaVersion;
    @Column(name = "definition_json", nullable = false, length = 24000) private String definitionJson;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected ProjectTemplate() { }

    public static ProjectTemplate create(UserAccount owner, String name, String description,
            String definitionJson, int schemaVersion, Instant now) {
        ProjectTemplate template = new ProjectTemplate();
        template.id = UUID.randomUUID(); template.owner = owner; template.name = name.trim();
        template.description = description == null ? "" : description.trim();
        template.definitionJson = definitionJson; template.schemaVersion = schemaVersion;
        template.createdAt = now; template.updatedAt = now;
        return template;
    }

    public UUID getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public int getSchemaVersion() { return schemaVersion; }
    public String getDefinitionJson() { return definitionJson; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
