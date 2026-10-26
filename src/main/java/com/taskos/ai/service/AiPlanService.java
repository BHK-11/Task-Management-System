package com.taskos.ai.service;

import com.taskos.ai.dto.AiActionType;
import com.taskos.ai.dto.AiPlanStatus;
import com.taskos.ai.dto.AiPlannerPlanDraft;
import com.taskos.ai.dto.AiPlannerRequest;
import com.taskos.ai.dto.AiPlannerResponse;
import com.taskos.ai.dto.AiPlannerStepDraft;
import com.taskos.ai.dto.AiWorkPlannerPlanDraft;
import com.taskos.ai.dto.AiWorkPlannerResponse;
import com.taskos.ai.dto.AiWorkPlannerStepDraft;
import com.taskos.ai.dto.AiUsageSummary;
import com.taskos.ai.entity.AiPlan;
import com.taskos.ai.entity.AiPlanStep;
import com.taskos.ai.repository.AiPlanRepository;
import com.taskos.knowledge.service.AiMemoryService;
import com.taskos.task.entity.TaskPriority;
import com.taskos.time.service.TimeZoneResolver;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiPlanService {
    private static final int MAX_STEPS = 12;
    private static final int MAX_DAILY_PLANS = 10;

    private final AiPlannerClient planner;
    private final AiPlanPersistenceService persistence;
    private final AiPlanRepository plans;
    private final AiModelPreferenceService modelPreferences;
    private final AiMemoryService memories;
    private final TimeZoneResolver zones;
    private final Clock clock;

    public AiPlanService(AiPlannerClient planner, AiPlanPersistenceService persistence,
            AiPlanRepository plans, AiModelPreferenceService modelPreferences, AiMemoryService memories,
            TimeZoneResolver zones, Clock clock) {
        this.planner = planner;
        this.persistence = persistence;
        this.plans = plans;
        this.modelPreferences = modelPreferences;
        this.memories = memories;
        this.zones = zones;
        this.clock = clock;
    }

    public boolean isConfigured() { return planner.isConfigured(); }

    @Transactional(readOnly = true)
    public AiUsageSummary usageThisMonth(UUID ownerId) {
        Instant monthStart = clock.instant().atZone(java.time.ZoneOffset.UTC)
                .withDayOfMonth(1).toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
        Object[] totals = plans.summarizeUsage(ownerId, monthStart).getFirst();
        return new AiUsageSummary(((Number) totals[0]).longValue(), ((Number) totals[1]).longValue(),
                ((Number) totals[2]).longValue());
    }

    public AiPlan plan(UUID ownerId, String rawInstruction) {
        String instruction = rawInstruction == null ? "" : rawInstruction.strip();
        if (instruction.isEmpty() || instruction.length() > 4000) {
            throw new AiCopilotException("Enter an instruction under 4,000 characters.");
        }
        Instant now = clock.instant();
        if (plans.countByOwner_IdAndCreatedAtAfter(ownerId, now.minusSeconds(86_400)) >= MAX_DAILY_PLANS) {
            throw new AiCopilotException("You have reached today's planning limit. Try again later.");
        }
        var zone = zones.zone(ownerId);
        var preference = modelPreferences.get(ownerId);
        AiPlannerResponse response = planner.plan(request(ownerId, instruction, zone.getId(), LocalDate.now(clock.withZone(zone)), preference));
        validate(response);
        return persistence.save(ownerId, instruction, response, now);
    }

    public AiPlan planWork(UUID ownerId, String rawInstruction) {
        String instruction = rawInstruction == null ? "" : rawInstruction.strip();
        if (instruction.isEmpty() || instruction.length() > 20000)
            throw new AiCopilotException("Enter an instruction under 20,000 characters.");
        Instant now = clock.instant();
        if (plans.countByOwner_IdAndCreatedAtAfter(ownerId, now.minusSeconds(86_400)) >= MAX_DAILY_PLANS)
            throw new AiCopilotException("You have reached today's planning limit. Try again later.");
        var zone = zones.zone(ownerId);
        var preference = modelPreferences.get(ownerId);
        AiWorkPlannerResponse response = planner.planWork(request(ownerId, instruction, zone.getId(), LocalDate.now(clock.withZone(zone)), preference));
        validateWork(response);
        return persistence.saveWork(ownerId, instruction, response, now);
    }

    private AiPlannerRequest request(UUID ownerId, String instruction, String timezone, LocalDate today,
            AiModelPreferenceService.Selection preference) {
        List<String> memoryContext = memories.retrieve(ownerId, instruction, 4).stream()
                .map(memory -> {
                    String content = memory.getContent();
                    int points = content.codePointCount(0, content.length());
                    return points > 500 ? content.substring(0, content.offsetByCodePoints(0, 500)) : content;
                })
                .toList();
        return new AiPlannerRequest(instruction, timezone, today, preference.mode().name(),
                preference.providerId() == null ? "" : preference.providerId(),
                preference.modelId() == null ? "" : preference.modelId(),
                preference.allowCloudProcessing(), preference.allowFallback(), memoryContext);
    }

    @Transactional(readOnly = true)
    public List<AiPlan> history(UUID ownerId) {
        return plans.findAllByOwner_IdOrderByCreatedAtDesc(ownerId, PageRequest.of(0, 10));
    }

    @Transactional(readOnly = true)
    public AiPlan detail(UUID ownerId, UUID planId) {
        return plans.findByIdAndOwnerId(planId, ownerId)
                .orElseThrow(() -> new AiCopilotException("That plan is not available."));
    }

    @Transactional
    public void edit(UUID ownerId, UUID planId, List<UUID> stepIds, List<String> titles,
            List<String> descriptions, List<String> priorities, List<String> dueDates) {
        AiPlan plan = lockedPlan(ownerId, planId);
        requirePending(plan);
        List<AiPlanStep> editable = plan.getSteps().stream()
                .filter(step -> step.getActionType() == AiActionType.CREATE_TASK).toList();
        if (stepIds == null || titles == null || descriptions == null || priorities == null || dueDates == null
                || stepIds.size() != editable.size() || titles.size() != editable.size()
                || descriptions.size() != editable.size() || priorities.size() != editable.size()
                || dueDates.size() != editable.size() || new HashSet<>(stepIds).size() != editable.size()) {
            throw new AiCopilotException("The plan fields changed. Reload the plan and try again.");
        }
        Map<UUID, AiPlanStep> byId = new HashMap<>();
        editable.forEach(step -> byId.put(step.getId(), step));
        for (int i = 0; i < stepIds.size(); i++) {
            AiPlanStep step = byId.remove(stepIds.get(i));
            String title = titles.get(i) == null ? "" : titles.get(i).strip();
            String description = descriptions.get(i) == null ? "" : descriptions.get(i).strip();
            if (step == null || title.isEmpty() || title.length() > 160 || description.length() > 6000) {
                throw new AiCopilotException("Check the task title and description.");
            }
            TaskPriority priority;
            LocalDate dueDate;
            try {
                priority = TaskPriority.valueOf(priorities.get(i));
                dueDate = dueDates.get(i) == null || dueDates.get(i).isBlank()
                        ? null : LocalDate.parse(dueDates.get(i));
            } catch (RuntimeException ex) {
                throw new AiCopilotException("Check the task priority and due date.");
            }
            step.update(title, description, priority, dueDate);
        }
        if (!byId.isEmpty()) throw new AiCopilotException("The plan fields changed. Reload the plan and try again.");
    }

    @Transactional
    public void editWork(UUID ownerId, UUID planId, List<UUID> stepIds, List<String> titles,
            List<String> descriptions, List<String> priorities, List<String> dueDates, List<String> contents) {
        AiPlan plan = lockedPlan(ownerId, planId);
        requirePending(plan);
        List<AiPlanStep> editable = plan.getSteps().stream()
                .filter(step -> step.getActionType() != AiActionType.UNSUPPORTED).toList();
        if (stepIds == null || titles == null || descriptions == null || priorities == null || dueDates == null || contents == null
                || stepIds.size() != editable.size() || titles.size() != editable.size()
                || descriptions.size() != editable.size() || priorities.size() != editable.size()
                || dueDates.size() != editable.size() || contents.size() != editable.size()
                || new HashSet<>(stepIds).size() != editable.size())
            throw new AiCopilotException("The plan fields changed. Reload the plan and try again.");
        Map<UUID, AiPlanStep> byId = new HashMap<>();
        editable.forEach(step -> byId.put(step.getId(), step));
        for (int i = 0; i < stepIds.size(); i++) {
            AiPlanStep step = byId.remove(stepIds.get(i));
            String title = titles.get(i) == null ? "" : titles.get(i).strip();
            String description = descriptions.get(i) == null ? "" : descriptions.get(i).strip();
            String content = contents.get(i) == null ? "" : contents.get(i).strip();
            if (step == null || title.isEmpty() || title.length() > 160 || description.length() > 6000 || content.length() > 12000
                    || (step.getActionType() != AiActionType.CREATE_TASK && content.isEmpty()))
                throw new AiCopilotException("Check the deliverable title, description, and content.");
            TaskPriority priority = step.getPriority();
            LocalDate dueDate = step.getDueDate();
            if (step.getActionType() == AiActionType.CREATE_TASK) {
                try {
                    priority = TaskPriority.valueOf(priorities.get(i));
                    dueDate = dueDates.get(i) == null || dueDates.get(i).isBlank() ? null : LocalDate.parse(dueDates.get(i));
                } catch (RuntimeException ex) {
                    throw new AiCopilotException("Check the task priority and due date.");
                }
            }
            step.updateWork(title, description, content, priority, dueDate);
        }
        if (!byId.isEmpty()) throw new AiCopilotException("The plan fields changed. Reload the plan and try again.");
    }

    @Transactional
    public AiPlan approve(UUID ownerId, UUID planId) {
        AiPlan plan = lockedPlan(ownerId, planId);
        requirePending(plan);
        if (!plan.getClarifications().isBlank()) {
            throw new AiCopilotException("Resolve the clarification by updating your instruction before approving.");
        }
        if (plan.getSteps().stream().noneMatch(step -> step.getActionType() != AiActionType.UNSUPPORTED)) {
            throw new AiCopilotException("This plan has no supported actions to approve.");
        }
        plan.queue(clock.instant());
        return plan;
    }

    @Transactional
    public void reject(UUID ownerId, UUID planId) {
        AiPlan plan = lockedPlan(ownerId, planId);
        requirePending(plan);
        plan.decide(AiPlanStatus.REJECTED, clock.instant());
    }

    static void validate(AiPlannerResponse response) {
        if (response == null || response.plan() == null || response.model() == null
                || response.model().isBlank() || response.model().length() > 128
                || (response.inputTokens() != null && response.inputTokens() < 0)
                || (response.outputTokens() != null && response.outputTokens() < 0)) {
            throw new AiCopilotException("The AI planner returned an incomplete plan.");
        }
        AiPlannerPlanDraft plan = response.plan();
        if (plan.summary() == null || plan.summary().isBlank() || plan.summary().length() > 1200
                || plan.detectedLanguage() == null
                || !Set.of("en", "hi", "hinglish", "mixed", "unknown").contains(plan.detectedLanguage())
                || plan.steps() == null || plan.steps().size() > MAX_STEPS
                || plan.clarifications() == null || plan.clarifications().size() > 8
                || plan.clarifications().stream().anyMatch(java.util.Objects::isNull)
                || String.join("\n", plan.clarifications()).length() > 1200) {
            throw new AiCopilotException("The AI planner returned an invalid plan.");
        }
        List<AiPlannerStepDraft> steps = plan.steps();
        for (AiPlannerStepDraft step : steps) {
            if (step == null || step.actionType() == null || step.title() == null || step.title().isBlank()
                    || step.title().length() > 160 || step.description() == null || step.description().length() > 6000
                    || (step.actionType() == AiActionType.CREATE_TASK && step.priority() == null)) {
                throw new AiCopilotException("The AI planner returned an invalid step.");
            }
        }
        topologicalOrderDrafts(steps);
    }

    static void validateWork(AiWorkPlannerResponse response) {
        if (response == null || response.plan() == null || response.model() == null || response.model().isBlank()
                || response.model().length() > 128 || (response.inputTokens() != null && response.inputTokens() < 0)
                || (response.outputTokens() != null && response.outputTokens() < 0))
            throw new AiCopilotException("The AI planner returned an incomplete work plan.");
        AiWorkPlannerPlanDraft plan = response.plan();
        if (plan.summary() == null || plan.summary().isBlank() || plan.summary().length() > 1200
                || plan.detectedLanguage() == null || !Set.of("en", "hi", "hinglish", "mixed", "unknown").contains(plan.detectedLanguage())
                || plan.steps() == null || plan.steps().size() > MAX_STEPS || plan.clarifications() == null
                || plan.clarifications().size() > 8 || plan.clarifications().stream().anyMatch(java.util.Objects::isNull)
                || String.join("\n", plan.clarifications()).length() > 1200)
            throw new AiCopilotException("The AI planner returned an invalid work plan.");
        for (AiWorkPlannerStepDraft step : plan.steps()) {
            if (step == null || step.actionType() == null || step.title() == null || step.title().isBlank()
                    || step.title().length() > 160 || step.description() == null || step.description().length() > 6000
                    || (step.actionType() == AiActionType.CREATE_TASK && step.priority() == null)
                    || (step.content() != null && step.content().length() > 12000)
                    || (step.actionType() != AiActionType.CREATE_TASK && step.actionType() != AiActionType.UNSUPPORTED
                    && (step.content() == null || step.content().isBlank())))
                throw new AiCopilotException("The AI planner returned an invalid work step.");
        }
        topologicalOrderDrafts(plan.steps().stream().map(step -> new AiPlannerStepDraft(step.actionType(), step.title(),
                step.description(), step.priority(), step.dueDate(), step.dependencies())).toList());
    }

    // shortcut: only owner-scoped tasks and bounded local deliverables are executable; register a scoped tool before adding external side effects.
    static List<Integer> topologicalOrderDrafts(List<AiPlannerStepDraft> steps) {
        int size = steps.size();
        int[] indegree = new int[size];
        List<List<Integer>> children = new ArrayList<>();
        for (int i = 0; i < size; i++) children.add(new ArrayList<>());
        for (int i = 0; i < size; i++) {
            List<Integer> dependencies = steps.get(i).dependencies();
            if (dependencies == null) throw new AiCopilotException("The AI planner returned an invalid dependency.");
            Set<Integer> unique = new HashSet<>();
            for (Integer dependency : dependencies) {
                if (dependency == null || dependency < 0 || dependency >= size || dependency == i || !unique.add(dependency)) {
                    throw new AiCopilotException("The AI planner returned an invalid dependency.");
                }
                indegree[i]++;
                children.get(dependency).add(i);
            }
        }
        PriorityQueue<Integer> ready = new PriorityQueue<>();
        for (int i = 0; i < size; i++) if (indegree[i] == 0) ready.add(i);
        List<Integer> order = new ArrayList<>(size);
        while (!ready.isEmpty()) {
            int current = ready.remove();
            order.add(current);
            for (int child : children.get(current)) if (--indegree[child] == 0) ready.add(child);
        }
        if (order.size() != size) throw new AiCopilotException("The AI planner returned a cyclic plan.");
        return order;
    }

    private AiPlan lockedPlan(UUID ownerId, UUID planId) {
        return plans.findLockedByIdAndOwnerId(planId, ownerId)
                .orElseThrow(() -> new AiCopilotException("That plan is not available."));
    }

    private void requirePending(AiPlan plan) {
        if (plan.getStatus() != AiPlanStatus.PENDING_APPROVAL) {
            throw new AiCopilotException("This plan has already been reviewed.");
        }
    }
}
