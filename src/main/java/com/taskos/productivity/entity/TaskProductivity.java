package com.taskos.productivity.entity;

import com.taskos.task.entity.Task;
import com.taskos.user.entity.UserAccount;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** Optional, shared methodology attributes for one canonical task. */
@Entity
@Table(name = "task_productivity", schema = "task_os")
public class TaskProductivity {
    @Id
    @Column(name = "task_id", nullable = false, updatable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false, insertable = false, updatable = false)
    private Task task;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    private Boolean urgent;
    private Boolean important;

    @Enumerated(EnumType.STRING)
    @Column(name = "gtd_state", length = 20)
    private GtdState gtdState;

    @Column(name = "waiting_for", length = 160)
    private String waitingFor;

    @Column(name = "follow_up_date")
    private LocalDate followUpDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "abc_category", length = 1)
    private AbcCategory abcCategory;

    @Column(name = "abc_rank")
    private Integer abcRank;

    @Enumerated(EnumType.STRING)
    @Column(name = "moscow_category", length = 16)
    private MoscowCategory moscowCategory;

    @Column(name = "impact_score")
    private Integer impactScore;

    @Column(name = "effort_score")
    private Integer effortScore;

    @Column(name = "impact_note", length = 500)
    private String impactNote;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private Long version;

    protected TaskProductivity() { }

    public static TaskProductivity create(Task task, UserAccount owner, Instant now) {
        TaskProductivity metadata = new TaskProductivity();
        metadata.id = task.getId();
        metadata.task = task;
        metadata.owner = owner;
        metadata.updatedAt = now;
        return metadata;
    }

    public void classifyEisenhower(Boolean urgent, Boolean important, Instant now) {
        if ((urgent == null) != (important == null)) throw new IllegalArgumentException("Set both urgency and importance, or leave both unclassified.");
        this.urgent = urgent;
        this.important = important;
        touch(now);
    }

    public void setGtd(GtdState state, String waitingFor, LocalDate followUpDate, Instant now) {
        this.gtdState = state;
        this.waitingFor = state == GtdState.WAITING_FOR ? clean(waitingFor) : null;
        this.followUpDate = state == GtdState.WAITING_FOR ? followUpDate : null;
        touch(now);
    }

    public void setAbc(AbcCategory category, int rank, Instant now) {
        this.abcCategory = category;
        this.abcRank = category == null ? null : rank;
        touch(now);
    }

    public void setMoscow(MoscowCategory category, Instant now) { this.moscowCategory = category; touch(now); }

    public void assessImpact(Integer impact, Integer effort, String note, Instant now) {
        if (impact != null && (impact < 1 || impact > 5)) throw new IllegalArgumentException("Impact must be between 1 and 5.");
        if (effort != null && (effort < 1 || effort > 5)) throw new IllegalArgumentException("Effort must be between 1 and 5.");
        if ((impact == null) != (effort == null)) throw new IllegalArgumentException("Provide both scores, or clear both scores.");
        this.impactScore = impact;
        this.effortScore = effort;
        this.impactNote = clean(note);
        touch(now);
    }

    private void touch(Instant now) { updatedAt = now; }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }

    public UUID getId() { return id; }
    public Task getTask() { return task; }
    public UserAccount getOwner() { return owner; }
    public Boolean getUrgent() { return urgent; }
    public Boolean getImportant() { return important; }
    public GtdState getGtdState() { return gtdState; }
    public String getWaitingFor() { return waitingFor; }
    public LocalDate getFollowUpDate() { return followUpDate; }
    public AbcCategory getAbcCategory() { return abcCategory; }
    public Integer getAbcRank() { return abcRank; }
    public MoscowCategory getMoscowCategory() { return moscowCategory; }
    public Integer getImpactScore() { return impactScore; }
    public Integer getEffortScore() { return effortScore; }
    public String getImpactNote() { return impactNote; }
    public Instant getUpdatedAt() { return updatedAt; }
}
