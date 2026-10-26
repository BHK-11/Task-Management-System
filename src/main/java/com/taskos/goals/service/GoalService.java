package com.taskos.goals.service;

import com.taskos.goals.dto.GoalDetail;
import com.taskos.goals.dto.GoalForm;
import com.taskos.goals.dto.GoalProgress;
import com.taskos.goals.dto.GoalListItem;
import com.taskos.goals.entity.Goal;
import com.taskos.goals.entity.GoalMeasurement;
import com.taskos.goals.entity.GoalMetric;
import com.taskos.goals.entity.GoalMilestone;
import com.taskos.goals.entity.GoalStatus;
import com.taskos.goals.entity.GoalTaskLink;
import com.taskos.goals.repository.GoalMeasurementRepository;
import com.taskos.goals.repository.GoalMilestoneRepository;
import com.taskos.goals.repository.GoalRepository;
import com.taskos.goals.repository.GoalTaskLinkRepository;
import com.taskos.project.entity.Project;
import com.taskos.project.repository.ProjectMilestoneRepository;
import com.taskos.project.repository.ProjectRepository;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.repository.TaskRepository;
import com.taskos.time.service.TimeZoneResolver;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Collections;
import java.util.UUID;
import java.util.ArrayList;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoalService {
    private final GoalRepository goals;
    private final GoalMeasurementRepository measurements;
    private final GoalMilestoneRepository milestones;
    private final GoalTaskLinkRepository taskLinks;
    private final TaskRepository tasks;
    private final ProjectRepository projects;
    private final Clock clock;
    private final TimeZoneResolver zones;

    public GoalService(GoalRepository goals, GoalMeasurementRepository measurements, GoalMilestoneRepository milestones,
            GoalTaskLinkRepository taskLinks, TaskRepository tasks, ProjectRepository projects, Clock clock, TimeZoneResolver zones) {
        this.goals = goals; this.measurements = measurements; this.milestones = milestones;
        this.taskLinks = taskLinks; this.tasks = tasks; this.projects = projects; this.clock = clock; this.zones = zones;
    }

    @Transactional(readOnly = true)
    public List<Goal> list(UUID ownerId) { return goals.findAllByOwnerIdAndArchivedAtIsNullOrderByTargetDateAscUpdatedAtDesc(ownerId); }

    @Transactional(readOnly = true)
    public List<GoalListItem> listItems(UUID ownerId) {
        List<Goal> owned = list(ownerId); if (owned.isEmpty()) return List.of();
        List<UUID> ids = owned.stream().map(Goal::getId).toList();
        Map<UUID, GoalMeasurement> latest = new HashMap<>();
        measurements.findAllByOwnerIdAndGoalIdInOrderByMeasurementDateDescRecordedAtDesc(ownerId, ids)
                .forEach(item -> latest.putIfAbsent(item.getGoalId(), item));
        Map<UUID, List<GoalMilestone>> milestonesByGoal = new HashMap<>();
        milestones.findAllByOwnerIdAndGoalIdInOrderByGoalIdAscPositionAsc(ownerId, ids)
                .forEach(item -> milestonesByGoal.computeIfAbsent(item.getGoalId(), ignored -> new java.util.ArrayList<>()).add(item));
        Map<UUID, List<UUID>> taskIdsByGoal = new HashMap<>(); List<GoalTaskLink> links = taskLinks.findAllByOwnerIdAndGoalIdIn(ownerId, ids);
        links.forEach(link -> taskIdsByGoal.computeIfAbsent(link.getGoalId(), ignored -> new java.util.ArrayList<>()).add(link.getTaskId()));
        List<Task> linkedTasks = taskIdsByGoal.isEmpty() ? List.of() : tasks.findAllByOwner_IdAndIdIn(ownerId, links.stream().map(GoalTaskLink::getTaskId).distinct().toList());
        Map<UUID, Task> tasksById = new HashMap<>(); linkedTasks.forEach(task -> tasksById.put(task.getId(), task));
        return owned.stream().map(goal -> {
            List<Task> linked = taskIdsByGoal.getOrDefault(goal.getId(), List.of()).stream().map(tasksById::get).filter(java.util.Objects::nonNull).toList();
            List<GoalMeasurement> history = latest.containsKey(goal.getId()) ? List.of(latest.get(goal.getId())) : List.of();
            return new GoalListItem(goal, progress(goal, history, milestonesByGoal.getOrDefault(goal.getId(), List.of()), linked));
        }).toList();
    }

    @Transactional
    public UUID create(UUID ownerId, GoalForm form) {
        validate(form);
        if (form.getProjectId() != null) ownProject(ownerId, form.getProjectId());
        Goal goal = goals.save(Goal.create(ownerId, form.getTitle(), form.getDescription(), form.getSpecificOutcome(),
                form.getMeasurementDefinition(), form.getWhyItMatters(), form.getFeasibilityNotes(), form.getCategory(),
                form.getMetricType(), form.getBaselineValue(), form.getTargetValue(), form.getUnit(), form.getStartDate(),
                form.getTargetDate(), form.getProjectId(), clock.instant()));
        if (isNumeric(goal.getMetricType()) && goal.getBaselineValue() != null)
            measurements.save(GoalMeasurement.record(ownerId, goal, goal.getBaselineValue(), goal.getStartDate(), "Starting baseline", clock.instant()));
        return goal.getId();
    }

    @Transactional
    public void update(UUID ownerId, UUID id, GoalForm form) {
        validate(form);
        Goal goal = ownGoal(ownerId, id);
        if (form.getProjectId() != null) ownProject(ownerId, form.getProjectId());
        goal.update(form.getTitle(), form.getDescription(), form.getSpecificOutcome(), form.getMeasurementDefinition(),
                form.getWhyItMatters(), form.getFeasibilityNotes(), form.getCategory(), form.getMetricType(),
                form.getBaselineValue(), form.getTargetValue(), form.getUnit(), form.getStartDate(), form.getTargetDate(),
                form.getProjectId(), clock.instant());
    }

    @Transactional(readOnly = true)
    public GoalForm form(UUID ownerId, UUID id) {
        Goal goal = ownGoal(ownerId, id); GoalForm form = new GoalForm();
        form.setTitle(goal.getTitle()); form.setDescription(goal.getDescription()); form.setSpecificOutcome(goal.getSpecificOutcome());
        form.setMeasurementDefinition(goal.getMeasurementDefinition()); form.setWhyItMatters(goal.getWhyItMatters());
        form.setFeasibilityNotes(goal.getFeasibilityNotes()); form.setCategory(goal.getCategory()); form.setMetricType(goal.getMetricType());
        form.setBaselineValue(goal.getBaselineValue()); form.setTargetValue(goal.getTargetValue()); form.setUnit(goal.getUnit());
        form.setStartDate(goal.getStartDate()); form.setTargetDate(goal.getTargetDate()); form.setProjectId(goal.getProjectId()); return form;
    }

    public List<String> qualityGuidance(GoalForm form) {
        List<String> guidance = new ArrayList<>();
        if (form.getSpecificOutcome() == null || form.getSpecificOutcome().isBlank()) guidance.add("Describe the specific result you want to achieve.");
        if (isNumeric(form.getMetricType()) && (form.getTargetValue() == null || form.getMeasurementDefinition() == null || form.getMeasurementDefinition().isBlank()))
            guidance.add("Add a measurable success criterion.");
        if (form.getTargetDate() == null) guidance.add("Choose a target date.");
        if (guidance.isEmpty()) guidance.add("Your goal has an outcome, a measurement, and a review date. You decide whether the plan is feasible.");
        return List.copyOf(guidance);
    }

    @Transactional
    public void record(UUID ownerId, UUID id, BigDecimal value, LocalDate date, String notes) {
        Goal goal = ownGoal(ownerId, id);
        if (!isNumeric(goal.getMetricType())) throw new IllegalArgumentException("This goal uses milestone, task or manual progress.");
        if (date == null || date.isAfter(LocalDate.now(clock.withZone(zones.zone(ownerId))))) throw new IllegalArgumentException("Choose a measurement date that is not in the future.");
        if (value == null) throw new IllegalArgumentException("Enter a measurement value.");
        measurements.save(GoalMeasurement.record(ownerId, goal, value, date, notes, clock.instant()));
    }

    @Transactional
    public void transition(UUID ownerId, UUID id, GoalStatus status) { ownGoal(ownerId, id).transition(status, clock.instant()); }

    @Transactional
    public void archive(UUID ownerId, UUID id) { ownGoal(ownerId, id).archive(clock.instant()); }

    @Transactional
    public void linkTask(UUID ownerId, UUID goalId, UUID taskId) {
        Goal goal = ownGoal(ownerId, goalId);
        if (tasks.findDetailByIdAndOwner(taskId, ownerId).isEmpty()) throw new IllegalArgumentException("Choose one of your own tasks.");
        if (taskLinks.findByOwnerIdAndGoalIdAndTaskId(ownerId, goalId, taskId).isEmpty())
            taskLinks.save(new GoalTaskLink(ownerId, goal.getId(), taskId, clock.instant()));
    }

    @Transactional
    public void unlinkTask(UUID ownerId, UUID goalId, UUID taskId) {
        GoalTaskLink link = taskLinks.findByOwnerIdAndGoalIdAndTaskId(ownerId, goalId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("That task link is not available."));
        ownGoal(ownerId, goalId); taskLinks.delete(link);
    }

    @Transactional
    public void addMilestone(UUID ownerId, UUID goalId, String title, String description, LocalDate date) {
        Goal goal = ownGoal(ownerId, goalId);
        if (title == null || title.isBlank() || title.strip().length() > 160 || (description != null && description.length() > 1000))
            throw new IllegalArgumentException("Enter a milestone title up to 160 characters and a description up to 1,000 characters.");
        var current = milestones.findAllByOwnerIdAndGoalIdOrderByPositionAsc(ownerId, goalId);
        milestones.save(GoalMilestone.create(ownerId, goal.getId(), title, description, date, current.size(), clock.instant()));
    }

    @Transactional
    public void toggleMilestone(UUID ownerId, UUID goalId, UUID milestoneId) {
        ownGoal(ownerId, goalId);
        milestones.findByIdAndOwnerIdAndGoalId(milestoneId, ownerId, goalId)
                .orElseThrow(() -> new IllegalArgumentException("That milestone is not available.")).toggle(clock.instant());
    }

    @Transactional
    public void updateMilestone(UUID ownerId, UUID goalId, UUID milestoneId, String title, String description, LocalDate date) {
        ownGoal(ownerId, goalId); validateMilestone(title, description);
        milestones.findByIdAndOwnerIdAndGoalId(milestoneId, ownerId, goalId)
                .orElseThrow(() -> new IllegalArgumentException("That milestone is not available.")).update(title, description, date, clock.instant());
    }

    @Transactional
    public void moveMilestone(UUID ownerId, UUID goalId, UUID milestoneId, int direction) {
        ownGoal(ownerId, goalId); if (direction != -1 && direction != 1) throw new IllegalArgumentException("Choose a valid roadmap direction.");
        List<GoalMilestone> ordered = new java.util.ArrayList<>(milestones.findAllByOwnerIdAndGoalIdOrderByPositionAsc(ownerId, goalId));
        int from = -1; for (int i = 0; i < ordered.size(); i++) if (ordered.get(i).getId().equals(milestoneId)) { from = i; break; }
        int to = from + direction; if (from < 0) throw new IllegalArgumentException("That milestone is not available.");
        if (to < 0 || to >= ordered.size()) return;
        Collections.swap(ordered, from, to); for (int i = 0; i < ordered.size(); i++) ordered.get(i).reorder(i, clock.instant());
    }

    @Transactional
    public void deleteMilestone(UUID ownerId, UUID goalId, UUID milestoneId) {
        ownGoal(ownerId, goalId);
        GoalMilestone found = milestones.findByIdAndOwnerIdAndGoalId(milestoneId, ownerId, goalId)
                .orElseThrow(() -> new IllegalArgumentException("That milestone is not available."));
        milestones.delete(found);
        List<GoalMilestone> ordered = milestones.findAllByOwnerIdAndGoalIdOrderByPositionAsc(ownerId, goalId);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).reorder(i, clock.instant());
    }

    @Transactional(readOnly = true)
    public GoalDetail detail(UUID ownerId, UUID id) {
        Goal goal = ownGoal(ownerId, id);
        List<GoalMeasurement> history = measurements.findAllByOwnerIdAndGoalIdOrderByMeasurementDateDescRecordedAtDesc(ownerId, id);
        List<GoalMilestone> roadmap = milestones.findAllByOwnerIdAndGoalIdOrderByPositionAsc(ownerId, id);
        List<Task> linked = tasks.findAllByOwner_IdAndIdIn(ownerId, taskLinks.findAllByOwnerIdAndGoalId(ownerId, id).stream().map(GoalTaskLink::getTaskId).toList());
        Project project = goal.getProjectId() == null ? null : projects.findByIdAndOwner_Id(goal.getProjectId(), ownerId).orElse(null);
        return new GoalDetail(goal, progress(goal, history, roadmap, linked), history, roadmap, linked, project);
    }

    @Transactional(readOnly = true)
    public GoalProgress progress(Goal goal) {
        return progress(goal, measurements.findAllByOwnerIdAndGoalIdOrderByMeasurementDateDescRecordedAtDesc(goal.getOwnerId(), goal.getId()),
                milestones.findAllByOwnerIdAndGoalIdOrderByPositionAsc(goal.getOwnerId(), goal.getId()),
                tasks.findAllByOwner_IdAndIdIn(goal.getOwnerId(), taskLinks.findAllByOwnerIdAndGoalId(goal.getOwnerId(), goal.getId()).stream().map(GoalTaskLink::getTaskId).toList()));
    }

    @Transactional(readOnly = true)
    public List<Task> taskOptions(UUID ownerId) { return tasks.findProductivityTasks(ownerId,
            EnumSet.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.ON_HOLD, TaskStatus.COMPLETED), PageRequest.of(0, 100)); }
    @Transactional(readOnly = true)
    public List<Project> projectOptions(UUID ownerId) { return projects.findAllByOwner_IdAndArchivedAtIsNullOrderByUpdatedAtDesc(ownerId); }
    @Transactional(readOnly = true)
    public long activeCount(UUID ownerId) { return goals.countByOwnerIdAndStatusAndArchivedAtIsNull(ownerId, GoalStatus.ACTIVE); }

    private GoalProgress progress(Goal goal, List<GoalMeasurement> history, List<GoalMilestone> roadmap, List<Task> linked) {
        BigDecimal current = history.isEmpty() ? null : history.getFirst().getRecordedValue();
        BigDecimal fraction = null; String label = "No measurement yet";
        switch (goal.getMetricType()) {
            case NUMERIC_INCREASE, PERCENT_COMPLETE -> {
                fraction = ProgressCalculator.fraction(goal.getBaselineValue(), goal.getTargetValue(), current);
                if (fraction != null) label = current.stripTrailingZeros().toPlainString() + " / " + goal.getTargetValue().stripTrailingZeros().toPlainString() + " " + goal.getUnit();
            }
            case NUMERIC_DECREASE -> {
                fraction = ProgressCalculator.fraction(goal.getBaselineValue(), goal.getTargetValue(), current);
                if (fraction != null) label = current.stripTrailingZeros().toPlainString() + " / " + goal.getTargetValue().stripTrailingZeros().toPlainString() + " " + goal.getUnit();
            }
            case MILESTONE_BASED -> {
                if (!roadmap.isEmpty()) { long done = roadmap.stream().filter(m -> "COMPLETED".equals(m.getStatus())).count(); fraction = BigDecimal.valueOf(done).divide(BigDecimal.valueOf(roadmap.size()), 8, java.math.RoundingMode.HALF_UP); label = done + " / " + roadmap.size() + " milestones"; }
            }
            case TASK_COMPLETION_BASED -> {
                List<Task> eligible = linked.stream().filter(t -> t.getParent() == null && t.getArchivedAt() == null && t.getStatus() != TaskStatus.CANCELLED).toList();
                if (!eligible.isEmpty()) { long done = eligible.stream().filter(t -> t.getStatus() == TaskStatus.COMPLETED).count(); fraction = BigDecimal.valueOf(done).divide(BigDecimal.valueOf(eligible.size()), 8, java.math.RoundingMode.HALF_UP); label = done + " / " + eligible.size() + " linked tasks"; }
            }
            case MANUAL_STATUS -> { if (goal.getStatus() == GoalStatus.COMPLETED) { fraction = BigDecimal.ONE; label = "Marked complete"; } }
        }
        return new GoalProgress(current, fraction, ProgressCalculator.displayPercent(fraction), label);
    }

    private Goal ownGoal(UUID ownerId, UUID id) { return goals.findByIdAndOwnerId(id, ownerId).orElseThrow(() -> new IllegalArgumentException("That goal is not available.")); }
    private Project ownProject(UUID ownerId, UUID id) { return projects.findByIdAndOwner_Id(id, ownerId).orElseThrow(() -> new IllegalArgumentException("Choose one of your own projects.")); }
    private void validate(GoalForm form) {
        if (form.getTitle() == null || form.getTitle().isBlank() || form.getTitle().strip().length() > 160)
            throw new IllegalArgumentException("Enter a goal title using 160 characters or fewer.");
        if (form.getStartDate() == null || (form.getTargetDate() != null && form.getTargetDate().isBefore(form.getStartDate()))) throw new IllegalArgumentException("The target date must be on or after the start date.");
        if (form.getMetricType() == null || form.getCategory() == null) throw new IllegalArgumentException("Choose a goal category and progress method.");
        if (isNumeric(form.getMetricType()) && (form.getBaselineValue() == null || form.getTargetValue() == null || form.getBaselineValue().compareTo(form.getTargetValue()) == 0))
            throw new IllegalArgumentException("Numeric goals need different baseline and target values.");
        if (form.getMetricType() == GoalMetric.NUMERIC_DECREASE && form.getTargetValue() != null && form.getBaselineValue() != null && form.getTargetValue().compareTo(form.getBaselineValue()) >= 0)
            throw new IllegalArgumentException("A decreasing goal target must be below its baseline.");
        if (form.getMetricType() == GoalMetric.NUMERIC_INCREASE && form.getTargetValue() != null && form.getBaselineValue() != null && form.getTargetValue().compareTo(form.getBaselineValue()) <= 0)
            throw new IllegalArgumentException("An increasing goal target must be above its baseline.");
        if (form.getMetricType() == GoalMetric.PERCENT_COMPLETE && (form.getBaselineValue() == null || form.getTargetValue() == null ||
                form.getBaselineValue().compareTo(BigDecimal.ZERO) < 0 || form.getBaselineValue().compareTo(BigDecimal.valueOf(100)) > 0 ||
                form.getTargetValue().compareTo(BigDecimal.valueOf(100)) > 0 ||
                form.getTargetValue().compareTo(form.getBaselineValue()) <= 0))
            throw new IllegalArgumentException("Percent goals must increase toward a target no greater than 100.");
    }
    private void validateMilestone(String title, String description) {
        if (title == null || title.isBlank() || title.strip().length() > 160 || (description != null && description.length() > 1000))
            throw new IllegalArgumentException("Enter a milestone title up to 160 characters and a description up to 1,000 characters.");
    }
    private boolean isNumeric(GoalMetric type) { return type == GoalMetric.NUMERIC_INCREASE || type == GoalMetric.NUMERIC_DECREASE || type == GoalMetric.PERCENT_COMPLETE; }
}
