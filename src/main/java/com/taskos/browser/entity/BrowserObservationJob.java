package com.taskos.browser.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "browser_observation_jobs", schema = "task_os")
public class BrowserObservationJob {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 255, updatable = false)
    private String origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private BrowserJobStatus status;

    @Column(name = "page_title", length = 240, updatable = false)
    private String pageTitle;

    @Column(name = "error_code", length = 40, updatable = false)
    private String errorCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected BrowserObservationJob() {}

    public static BrowserObservationJob record(UUID ownerId, String origin, BrowserJobStatus status,
            String pageTitle, String errorCode, Instant createdAt) {
        BrowserObservationJob job = new BrowserObservationJob();
        job.id = UUID.randomUUID();
        job.ownerId = ownerId;
        job.origin = origin;
        job.status = status;
        job.pageTitle = pageTitle;
        job.errorCode = errorCode;
        job.createdAt = createdAt;
        return job;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getOrigin() { return origin; }
    public BrowserJobStatus getStatus() { return status; }
    public String getPageTitle() { return pageTitle; }
    public String getErrorCode() { return errorCode; }
    public Instant getCreatedAt() { return createdAt; }
}
