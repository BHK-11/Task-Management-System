package com.taskos.project.entity;

import com.taskos.collaboration.entity.Workspace;
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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "projects", schema = "task_os")
public class Project {
    @Id private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(nullable = false, length = 160) private String name;
    @Column(nullable = false, length = 2000) private String description = "";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private ProjectStatus status;
    @Column(name = "start_date") private LocalDate startDate;
    @Column(name = "target_end_date") private LocalDate targetEndDate;
    @Column(nullable = false, length = 7) private String color = "#4f46e5";
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "archived_at") private Instant archivedAt;
    @Version private Long version;

    protected Project() { }

    public static Project create(UserAccount owner, String name, String description, ProjectStatus status,
            LocalDate startDate, LocalDate targetEndDate, String color, Instant now) {
        Project project = new Project();
        project.id = UUID.randomUUID();
        project.owner = owner;
        project.name = name.trim();
        project.description = description == null ? "" : description.trim();
        project.status = status == null ? ProjectStatus.PLANNED : status;
        project.startDate = startDate;
        project.targetEndDate = targetEndDate;
        project.color = color == null || color.isBlank() ? "#4f46e5" : color;
        project.createdAt = now;
        project.updatedAt = now;
        return project;
    }

    public void update(String name, String description, ProjectStatus status, LocalDate startDate,
            LocalDate targetEndDate, String color, Instant now) {
        if (this.status != status && !this.status.canTransitionTo(status))
            throw new IllegalArgumentException("That project status change is not available.");
        this.name = name.trim();
        this.description = description == null ? "" : description.trim();
        this.status = status;
        this.startDate = startDate;
        this.targetEndDate = targetEndDate;
        this.color = color;
        this.updatedAt = now;
    }

    public boolean archive(Instant now) {
        if (archivedAt != null) return false;
        archivedAt = now; updatedAt = now; return true;
    }

    public boolean restore(Instant now) {
        if (archivedAt == null) return false;
        archivedAt = null; updatedAt = now; return true;
    }

    public void shareWith(Workspace workspace, Instant now) {
        if (this.workspace != null) throw new IllegalStateException("This project already belongs to a workspace.");
        this.workspace = workspace; this.updatedAt = now;
    }

    public void touchSchedule(Instant now) { updatedAt = now; }

    public UUID getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public Workspace getWorkspace() { return workspace; }
    public UUID getWorkspaceId() { return workspace == null ? null : workspace.getId(); }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public ProjectStatus getStatus() { return status; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getTargetEndDate() { return targetEndDate; }
    public String getColor() { return color; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public long getVersion() { return version == null ? 0 : version; }
}
