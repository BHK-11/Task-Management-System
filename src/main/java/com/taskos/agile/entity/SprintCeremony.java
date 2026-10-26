package com.taskos.agile.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "sprint_ceremonies", schema = "task_os")
public class SprintCeremony {
    @Id @Column(name = "sprint_id") private UUID sprintId;
    @Column(name = "goal_outcome", nullable = false, length = 5000) private String goalOutcome = "";
    @Column(name = "delivered_work", nullable = false, length = 5000) private String deliveredWork = "";
    @Column(name = "incomplete_work", nullable = false, length = 5000) private String incompleteWork = "";
    @Column(name = "demo_notes", nullable = false, length = 5000) private String demoNotes = "";
    @Column(name = "stakeholder_feedback", nullable = false, length = 5000) private String stakeholderFeedback = "";
    @Column(name = "accepted_items", nullable = false, length = 5000) private String acceptedItems = "";
    @Column(name = "needs_rework_items", nullable = false, length = 5000) private String needsReworkItems = "";
    @Column(name = "review_follow_ups", nullable = false, length = 5000) private String reviewFollowUps = "";
    @Column(name = "went_well", nullable = false, length = 5000) private String wentWell = "";
    @Column(name = "could_improve", nullable = false, length = 5000) private String couldImprove = "";
    @Column(name = "try_next", nullable = false, length = 5000) private String tryNext = "";
    @Column(name = "retrospective_actions", nullable = false, length = 5000) private String retrospectiveActions = "";
    @Column(name = "updated_at", nullable = false) private Instant updatedAt;
    @Version private Long version;

    protected SprintCeremony() { }
    public static SprintCeremony create(UUID sprintId, Instant now) {
        SprintCeremony item = new SprintCeremony(); item.sprintId = sprintId; item.updatedAt = now; return item;
    }
    public void saveReview(String outcome, String delivered, String incomplete, String demo,
            String feedback, String accepted, String needsRework, String followUps, Instant now) {
        goalOutcome = clean(outcome); deliveredWork = clean(delivered); incompleteWork = clean(incomplete);
        demoNotes = clean(demo); stakeholderFeedback = clean(feedback); acceptedItems = clean(accepted);
        needsReworkItems = clean(needsRework); reviewFollowUps = clean(followUps); updatedAt = now;
    }
    public void saveRetrospective(String wentWell, String improve, String tryNext, String actions, Instant now) {
        this.wentWell = clean(wentWell); couldImprove = clean(improve); this.tryNext = clean(tryNext);
        retrospectiveActions = clean(actions); updatedAt = now;
    }
    private String clean(String value) { return value == null ? "" : value.trim(); }
    public UUID getSprintId() { return sprintId; }
    public String getGoalOutcome() { return goalOutcome; }
    public String getDeliveredWork() { return deliveredWork; }
    public String getIncompleteWork() { return incompleteWork; }
    public String getDemoNotes() { return demoNotes; }
    public String getStakeholderFeedback() { return stakeholderFeedback; }
    public String getAcceptedItems() { return acceptedItems; }
    public String getNeedsReworkItems() { return needsReworkItems; }
    public String getReviewFollowUps() { return reviewFollowUps; }
    public String getWentWell() { return wentWell; }
    public String getCouldImprove() { return couldImprove; }
    public String getTryNext() { return tryNext; }
    public String getRetrospectiveActions() { return retrospectiveActions; }
}
