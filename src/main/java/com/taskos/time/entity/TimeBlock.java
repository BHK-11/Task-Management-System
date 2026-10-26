package com.taskos.time.entity;

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
@Table(name = "time_blocks", schema = "task_os")
public class TimeBlock {
    @Id private UUID id;
    @Column(name = "owner_id", nullable = false, updatable = false) private UUID ownerId;
    @Column(name = "task_id") private UUID taskId;
    @Column(nullable = false, length = 160) private String title;
    @Column(name = "start_at", nullable = false) private Instant startAt;
    @Column(name = "end_at", nullable = false) private Instant endAt;
    @Column(name = "timezone_id", nullable = false, length = 64) private String timezoneId;
    @Column(nullable = false, length = 1200) private String notes = "";
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 16) private TimeBlockStatus status;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt;
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected TimeBlock() { }

    public static TimeBlock create(UUID ownerId, UUID taskId, String title, Instant startAt, Instant endAt,
            String timezoneId, String notes, Instant now) {
        TimeBlock block = new TimeBlock();
        block.id = UUID.randomUUID(); block.ownerId = ownerId; block.taskId = taskId;
        block.title = title; block.startAt = startAt; block.endAt = endAt; block.timezoneId = timezoneId;
        block.notes = notes == null ? "" : notes; block.status = TimeBlockStatus.PLANNED;
        block.createdAt = now; block.updatedAt = now;
        return block;
    }

    public void update(UUID taskId, String title, Instant startAt, Instant endAt, String timezoneId,
            String notes, Instant now) {
        this.taskId = taskId; this.title = title; this.startAt = startAt; this.endAt = endAt;
        this.timezoneId = timezoneId; this.notes = notes == null ? "" : notes; this.updatedAt = now;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public UUID getTaskId() { return taskId; }
    public String getTitle() { return title; }
    public Instant getStartAt() { return startAt; }
    public Instant getEndAt() { return endAt; }
    public String getTimezoneId() { return timezoneId; }
    public String getNotes() { return notes; }
    public TimeBlockStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version == null ? 0 : version; }
}
