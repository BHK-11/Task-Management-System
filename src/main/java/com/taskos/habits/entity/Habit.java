package com.taskos.habits.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "habits", schema = "task_os")
public class Habit {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, length = 1000) private String description;
    @Column(name = "start_date", nullable = false, updatable = false) private LocalDate startDate;
    @Column(name = "timezone_id", nullable = false, length = 64) private String timezoneId;
    @Column(name = "linked_goal_id") private UUID linkedGoalId;
    @Column(name = "linked_task_id") private UUID linkedTaskId;
    @Column(nullable = false, length = 16) private String status;
    @Column(name = "archived_at") private Instant archivedAt;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;
    protected Habit() { }
    public static Habit create(UUID ownerId, String name, String description, LocalDate startDate, String zone,
            UUID goalId, UUID taskId, Instant now) {
        var habit = new Habit(); habit.id = UUID.randomUUID(); habit.ownerId = ownerId; habit.name = name.strip();
        habit.description = description == null ? "" : description.strip(); habit.startDate = startDate; habit.timezoneId = zone;
        habit.linkedGoalId = goalId; habit.linkedTaskId = taskId; habit.status = "ACTIVE"; habit.createdAt = now; habit.updatedAt = now; return habit;
    }
    public void update(String name, String description, UUID goalId, UUID taskId, Instant now) {
        this.name = name.strip(); this.description = description == null ? "" : description.strip(); this.linkedGoalId = goalId; this.linkedTaskId = taskId; updatedAt = now;
    }
    public void archive(Instant now) { status = "ARCHIVED"; archivedAt = now; updatedAt = now; }
    public UUID getId() { return id; } public UUID getOwnerId() { return ownerId; } public String getName() { return name; }
    public String getDescription() { return description; } public LocalDate getStartDate() { return startDate; }
    public String getTimezoneId() { return timezoneId; } public UUID getLinkedGoalId() { return linkedGoalId; }
    public UUID getLinkedTaskId() { return linkedTaskId; } public String getStatus() { return status; } public Instant getArchivedAt() { return archivedAt; }
}
