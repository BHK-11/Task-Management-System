package com.taskos.task.entity;

import com.taskos.collaboration.entity.Workspace;
import com.taskos.user.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

@Entity
@Table(name = "tasks", schema = "task_os", indexes = {
        @Index(name = "ix_tasks_owner_created", columnList = "owner_id, created_at"),
        @Index(name = "ix_tasks_owner_status", columnList = "owner_id, status, updated_at"),
        @Index(name = "ix_tasks_owner_due", columnList = "owner_id, due_date"),
        @Index(name = "ix_tasks_owner_status_due", columnList = "owner_id, status, due_date"),
        @Index(name = "ix_tasks_owner_status_completed", columnList = "owner_id, status, completed_at"),
        @Index(name = "ix_tasks_owner_archived", columnList = "owner_id, archived_at, updated_at"),
        @Index(name = "ix_tasks_owner_parent", columnList = "owner_id, parent_id")
})
public class Task {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Task parent;

    @Column(name = "subtask_position")
    private Integer subtaskPosition;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "workspace_id")
    private Workspace workspace;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 6000)
    private String description = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskStatus status = TaskStatus.TODO;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private TaskPriority priority = TaskPriority.MEDIUM;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "task_tag_assignments", schema = "task_os",
            joinColumns = @JoinColumn(name = "task_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private Set<TaskTag> tags = new LinkedHashSet<>();

    protected Task() {
    }

    public static Task create(UserAccount owner, String title, String description, TaskStatus status,
            TaskPriority priority, LocalDate startDate, LocalDate dueDate, Task parent, Instant now) {
        Task task = new Task();
        task.id = UUID.randomUUID();
        task.owner = owner;
        task.parent = parent;
        task.title = title;
        task.description = description == null ? "" : description;
        task.status = status == null ? TaskStatus.TODO : status;
        task.completedAt = task.status == TaskStatus.COMPLETED ? now : null;
        task.priority = priority == null ? TaskPriority.MEDIUM : priority;
        task.startDate = startDate;
        task.dueDate = dueDate;
        task.createdAt = now;
        task.updatedAt = now;
        return task;
    }

    public static Task createInWorkspace(UserAccount creator, Workspace workspace, String title, String description,
            TaskStatus status, TaskPriority priority, LocalDate startDate, LocalDate dueDate, Instant now) {
        Task task = create(creator, title, description, status, priority, startDate, dueDate, null, now);
        task.workspace = workspace;
        return task;
    }

    public void shareWith(Workspace workspace) {
        if (this.workspace != null) throw new IllegalStateException("This task already belongs to a workspace.");
        if (parent != null) throw new IllegalStateException("Subtasks cannot be shared separately from their parent.");
        this.workspace = workspace;
    }

    public void updateDetails(String title, String description, TaskPriority priority,
            LocalDate startDate, LocalDate dueDate, Instant now) {
        this.title = title;
        this.description = description == null ? "" : description;
        this.priority = priority;
        this.startDate = startDate;
        this.dueDate = dueDate;
        this.updatedAt = now;
    }

    public void updatePlannedStartDate(LocalDate startDate, Instant now) {
        if (startDate != null && dueDate != null && startDate.isAfter(dueDate))
            throw new IllegalArgumentException("The planned start date cannot be after the due date.");
        this.startDate = startDate;
        this.updatedAt = now;
    }

    public boolean changeStatus(TaskStatus next, Instant now) {
        if (next == status) return false;
        if (!canTransition(status, next)) throw new IllegalArgumentException("That status change is not available.");
        status = next;
        completedAt = next == TaskStatus.COMPLETED ? now : null;
        updatedAt = now;
        return true;
    }

    private boolean canTransition(TaskStatus from, TaskStatus to) {
        return switch (from) {
            case TODO -> to == TaskStatus.IN_PROGRESS || to == TaskStatus.ON_HOLD
                    || to == TaskStatus.COMPLETED || to == TaskStatus.CANCELLED;
            case IN_PROGRESS -> to == TaskStatus.TODO || to == TaskStatus.ON_HOLD
                    || to == TaskStatus.COMPLETED || to == TaskStatus.CANCELLED;
            case ON_HOLD -> to == TaskStatus.TODO || to == TaskStatus.IN_PROGRESS
                    || to == TaskStatus.COMPLETED || to == TaskStatus.CANCELLED;
            case COMPLETED, CANCELLED -> to == TaskStatus.TODO || to == TaskStatus.IN_PROGRESS;
        };
    }

    public boolean archive(Instant now) {
        if (archivedAt != null) return false;
        archivedAt = now;
        updatedAt = now;
        return true;
    }

    public boolean restore(Instant now) {
        if (archivedAt == null) return false;
        archivedAt = null;
        updatedAt = now;
        return true;
    }

    public void touch(Instant now) { updatedAt = now; }

    public void moveSubtaskTo(int position, Instant now) {
        if (parent == null || position < 0) throw new IllegalArgumentException("Only subtasks can be ordered.");
        subtaskPosition = position;
        updatedAt = now;
    }

    public boolean assignTag(TaskTag tag, Instant now) {
        boolean changed = tags.add(tag);
        if (changed) updatedAt = now;
        return changed;
    }

    public boolean removeTag(TaskTag tag, Instant now) {
        boolean changed = tags.remove(tag);
        if (changed) updatedAt = now;
        return changed;
    }

    public boolean hasTag(UUID tagId) {
        return tags.stream().anyMatch(tag -> tag.getId().equals(tagId));
    }

    public UUID getParentId() { return parent == null ? null : parent.getId(); }
    public Integer getSubtaskPosition() { return subtaskPosition; }
    public UUID getId() { return id; }
    public UserAccount getOwner() { return owner; }
    public Task getParent() { return parent; }
    public Workspace getWorkspace() { return workspace; }
    public UUID getWorkspaceId() { return workspace == null ? null : workspace.getId(); }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public TaskStatus getStatus() { return status; }
    public TaskPriority getPriority() { return priority; }
    public LocalDate getStartDate() { return startDate; }
    public LocalDate getDueDate() { return dueDate; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public Instant getArchivedAt() { return archivedAt; }
    public long getVersion() { return version == null ? 0L : version; }
    public Set<TaskTag> getTags() { return Set.copyOf(tags); }
}
