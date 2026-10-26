package com.taskos.agile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "epics", schema = "task_os")
public class Epic {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 2000) private String description = "";
    @Column(name = "target_date") private LocalDate targetDate;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected Epic() { }
    public static Epic create(UUID projectId, String title, String description, LocalDate targetDate, Instant now) {
        Epic epic = new Epic(); epic.id = UUID.randomUUID(); epic.projectId = projectId;
        epic.title = title.trim(); epic.description = description == null ? "" : description.trim();
        epic.targetDate = targetDate; epic.createdAt = now; epic.updatedAt = now; return epic;
    }
    public void update(String title, String description, LocalDate targetDate, Instant now) {
        this.title = title.trim(); this.description = description == null ? "" : description.trim();
        this.targetDate = targetDate; this.updatedAt = now;
    }
    public void archive(Instant now) { archivedAt = now; updatedAt = now; }
    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getTargetDate() { return targetDate; }
    public Instant getArchivedAt() { return archivedAt; }
}
