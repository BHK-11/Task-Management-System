package com.taskos.ai.entity;

import com.taskos.ai.dto.AiPlanStatus;
import com.taskos.ai.dto.AiPlannerPlanDraft;
import com.taskos.ai.dto.AiPlannerResponse;
import com.taskos.user.entity.UserAccount;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "ai_plans", schema = "task_os", indexes =
        @Index(name = "ix_ai_plans_owner_created", columnList = "owner_id, created_at"))
public class AiPlan {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false, updatable = false)
    private UserAccount owner;

    @Column(name = "original_request", nullable = false, length = 20000, updatable = false)
    private String originalRequest;

    @Column(nullable = false, length = 1200)
    private String summary;

    @Column(name = "detected_language", nullable = false, length = 16)
    private String detectedLanguage;

    @Column(nullable = false, length = 1200)
    private String clarifications;

    @Column(name = "provider_model", nullable = false, length = 128, updatable = false)
    private String providerModel;

    @Column(name = "input_tokens")
    private Integer inputTokens;

    @Column(name = "output_tokens")
    private Integer outputTokens;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AiPlanStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "execution_lease_until")
    private Instant executionLeaseUntil;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("stepIndex ASC")
    private List<AiPlanStep> steps = new ArrayList<>();

    protected AiPlan() {}

    public static AiPlan create(UserAccount owner, String request, AiPlannerResponse response, Instant now) {
        AiPlannerPlanDraft draft = response.plan();
        AiPlan plan = new AiPlan();
        plan.id = UUID.randomUUID();
        plan.owner = owner;
        plan.originalRequest = request;
        plan.summary = draft.summary().strip();
        plan.detectedLanguage = draft.detectedLanguage();
        plan.clarifications = draft.clarifications() == null ? "" : String.join("\n", draft.clarifications());
        plan.providerModel = response.model();
        plan.inputTokens = response.inputTokens();
        plan.outputTokens = response.outputTokens();
        plan.status = AiPlanStatus.PENDING_APPROVAL;
        plan.createdAt = now;
        for (int i = 0; i < draft.steps().size(); i++) plan.steps.add(AiPlanStep.create(plan, i, draft.steps().get(i)));
        return plan;
    }

    public static AiPlan createWork(UserAccount owner, String request,
            com.taskos.ai.dto.AiWorkPlannerResponse response, Instant now) {
        var draft = response.plan();
        AiPlan plan = new AiPlan();
        plan.id = UUID.randomUUID();
        plan.owner = owner;
        plan.originalRequest = request;
        plan.summary = draft.summary().strip();
        plan.detectedLanguage = draft.detectedLanguage();
        plan.clarifications = draft.clarifications() == null ? "" : String.join("\n", draft.clarifications());
        plan.providerModel = response.model();
        plan.inputTokens = response.inputTokens();
        plan.outputTokens = response.outputTokens();
        plan.status = AiPlanStatus.PENDING_APPROVAL;
        plan.createdAt = now;
        for (int i = 0; i < draft.steps().size(); i++) plan.steps.add(AiPlanStep.createWork(plan, i, draft.steps().get(i)));
        return plan;
    }

    public void decide(AiPlanStatus status, Instant now) {
        this.status = status;
        this.decidedAt = now;
        this.executionLeaseUntil = null;
    }

    public void queue(Instant now) { status = AiPlanStatus.QUEUED; decidedAt = now; }
    public void claim(Instant leaseUntil) { status = AiPlanStatus.RUNNING; executionLeaseUntil = leaseUntil; }
    public void renewLease(Instant leaseUntil) { executionLeaseUntil = leaseUntil; }
    public void requeue() { status = AiPlanStatus.QUEUED; executionLeaseUntil = null; }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return owner.getId(); }
    public String getOriginalRequest() { return originalRequest; }
    public String getSummary() { return summary; }
    public String getDetectedLanguage() { return detectedLanguage; }
    public String getClarifications() { return clarifications; }
    public String getProviderModel() { return providerModel; }
    public Integer getInputTokens() { return inputTokens; }
    public Integer getOutputTokens() { return outputTokens; }
    public AiPlanStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getExecutionLeaseUntil() { return executionLeaseUntil; }
    public List<AiPlanStep> getSteps() { return steps; }
}
