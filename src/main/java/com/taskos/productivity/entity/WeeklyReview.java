package com.taskos.productivity.entity;

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
import java.time.LocalDate;
import java.util.UUID;

@Entity
@Table(name = "weekly_reviews", schema = "task_os")
public class WeeklyReview {
    @Id private UUID id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;
    @Column(name = "week_start", nullable = false, updatable = false)
    private LocalDate weekStart;
    @Column(name = "inbox_processed", nullable = false) private boolean inboxProcessed;
    @Column(name = "next_actions_reviewed", nullable = false) private boolean nextActionsReviewed;
    @Column(name = "waiting_for_reviewed", nullable = false) private boolean waitingForReviewed;
    @Column(name = "someday_reviewed", nullable = false) private boolean somedayReviewed;
    @Column(name = "commitments_reviewed", nullable = false) private boolean commitmentsReviewed;
    @Column(name = "completed_at") private Instant completedAt;
    @Version private Long version;

    protected WeeklyReview() { }
    public static WeeklyReview start(UserAccount owner, LocalDate weekStart) {
        WeeklyReview review = new WeeklyReview();
        review.id = UUID.randomUUID(); review.owner = owner; review.weekStart = weekStart;
        return review;
    }
    public void update(boolean inbox, boolean next, boolean waiting, boolean someday, boolean commitments, Instant now) {
        inboxProcessed = inbox; nextActionsReviewed = next; waitingForReviewed = waiting;
        somedayReviewed = someday; commitmentsReviewed = commitments;
        completedAt = inbox && next && waiting && someday && commitments ? now : null;
    }
    public UUID getId() { return id; }
    public LocalDate getWeekStart() { return weekStart; }
    public boolean isInboxProcessed() { return inboxProcessed; }
    public boolean isNextActionsReviewed() { return nextActionsReviewed; }
    public boolean isWaitingForReviewed() { return waitingForReviewed; }
    public boolean isSomedayReviewed() { return somedayReviewed; }
    public boolean isCommitmentsReviewed() { return commitmentsReviewed; }
    public Instant getCompletedAt() { return completedAt; }
}
