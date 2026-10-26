package com.taskos.project.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "project_milestones", schema = "task_os")
public class ProjectMilestone {
    @Id private UUID id;
    @Column(name = "project_id", nullable = false, updatable = false) private UUID projectId;
    @Column(nullable = false, length = 160) private String title;
    @Column(nullable = false, length = 1200) private String description = "";
    @Column(name = "target_date") private LocalDate targetDate;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private MilestoneStatus status;
    @Column(nullable = false) private int position;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Column(name = "completed_at") private Instant completedAt;
    @Version private Long version;

    protected ProjectMilestone() { }

    public static ProjectMilestone create(UUID projectId, String title, String description,
            LocalDate targetDate, int position, Instant now) {
        ProjectMilestone milestone = new ProjectMilestone();
        milestone.id = UUID.randomUUID(); milestone.projectId = projectId; milestone.title = title.trim();
        milestone.description = description == null ? "" : description.trim(); milestone.targetDate = targetDate;
        milestone.status = MilestoneStatus.PLANNED; milestone.position = position;
        milestone.createdAt = now; milestone.updatedAt = now;
        return milestone;
    }

    public void update(String title, String description, LocalDate targetDate, Instant now) {
        this.title = title.trim(); this.description = description == null ? "" : description.trim();
        this.targetDate = targetDate; this.updatedAt = now;
    }

    public void transition(MilestoneStatus next, Instant now) {
        if (status != next && !status.canTransitionTo(next))
            throw new IllegalArgumentException("That milestone status change is not available.");
        status = next; completedAt = next == MilestoneStatus.COMPLETED ? now : null; updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getProjectId() { return projectId; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDate getTargetDate() { return targetDate; }
    public MilestoneStatus getStatus() { return status; }
    public int getPosition() { return position; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public long getVersion() { return version == null ? 0 : version; }
}
