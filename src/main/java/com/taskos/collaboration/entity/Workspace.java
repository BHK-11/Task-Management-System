package com.taskos.collaboration.entity;

import com.taskos.user.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "workspaces", schema = "task_os")
public class Workspace {
    @Id private UUID id;
    @Column(nullable = false, length = 100) private String name;
    @Column(nullable = false, length = 1000) private String description = "";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private WorkspaceStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by_id", nullable = false, updatable = false)
    private UserAccount createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "archived_at") private Instant archivedAt;
    @Version private Long version;

    protected Workspace() { }

    public static Workspace create(UserAccount creator, String name, String description, Instant now) {
        Workspace workspace = new Workspace();
        workspace.id = UUID.randomUUID(); workspace.createdBy = creator; workspace.name = name.trim();
        workspace.description = description == null ? "" : description.trim(); workspace.status = WorkspaceStatus.ACTIVE;
        workspace.createdAt = now; workspace.updatedAt = now; return workspace;
    }

    public void update(String name, String description, Instant now) {
        this.name = name.trim(); this.description = description == null ? "" : description.trim(); this.updatedAt = now;
    }

    public void archive(Instant now) { status = WorkspaceStatus.ARCHIVED; archivedAt = now; updatedAt = now; }
    public void restore(Instant now) { status = WorkspaceStatus.ACTIVE; archivedAt = null; updatedAt = now; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public WorkspaceStatus getStatus() { return status; }
    public UserAccount getCreatedBy() { return createdBy; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public long getVersion() { return version == null ? 0 : version; }
}
