package com.taskos.scheduling.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.taskos.agile.entity.Sprint;
import com.taskos.agile.entity.SprintMembership;
import com.taskos.agile.entity.SprintMembershipStatus;
import com.taskos.agile.entity.SprintStatus;
import com.taskos.agile.repository.SprintMembershipRepository;
import com.taskos.agile.repository.SprintRepository;
import com.taskos.project.entity.Project;
import com.taskos.project.entity.ProjectMilestone;
import com.taskos.project.entity.ProjectTaskPlacement;
import com.taskos.project.repository.ProjectMilestoneRepository;
import com.taskos.project.repository.ProjectRepository;
import com.taskos.project.repository.ProjectTaskPlacementRepository;
import com.taskos.scheduling.dto.DependencyLine;
import com.taskos.scheduling.dto.DependencyGraphNode;
import com.taskos.scheduling.dto.MilestoneLine;
import com.taskos.scheduling.dto.ProjectSchedulePage;
import com.taskos.scheduling.dto.ScheduleBaselineSnapshot;
import com.taskos.scheduling.dto.ScheduleBaselineView;
import com.taskos.scheduling.dto.ScheduleChangePreview;
import com.taskos.scheduling.dto.ScheduleImpactLine;
import com.taskos.scheduling.dto.TaskScheduleLine;
import com.taskos.scheduling.dto.TimelineTick;
import com.taskos.scheduling.entity.ScheduleBaseline;
import com.taskos.scheduling.entity.TaskDependency;
import com.taskos.scheduling.entity.TaskDependencyType;
import com.taskos.scheduling.entity.TaskScheduleMetadata;
import com.taskos.scheduling.repository.ScheduleBaselineRepository;
import com.taskos.scheduling.repository.TaskDependencyRepository;
import com.taskos.scheduling.repository.TaskScheduleMetadataRepository;
import com.taskos.task.entity.Task;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskNotFoundException;
import com.taskos.task.service.TaskRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProjectScheduleService {
    private final ProjectRepository projects;
    private final ProjectTaskPlacementRepository placements;
    private final ProjectMilestoneRepository milestones;
    private final SprintRepository sprints;
    private final SprintMembershipRepository sprintMemberships;
    private final TaskRepository tasks;
    private final TaskScheduleMetadataRepository metadata;
    private final TaskDependencyRepository dependencies;
    private final ScheduleBaselineRepository baselines;
    private final CriticalPathCalculator calculator;
    private final ObjectMapper objectMapper;

    public ProjectScheduleService(ProjectRepository projects, ProjectTaskPlacementRepository placements,
            ProjectMilestoneRepository milestones, SprintRepository sprints, SprintMembershipRepository sprintMemberships,
            TaskRepository tasks, TaskScheduleMetadataRepository metadata,
            TaskDependencyRepository dependencies, ScheduleBaselineRepository baselines,
            CriticalPathCalculator calculator, ObjectMapper objectMapper) {
        this.projects = projects; this.placements = placements; this.milestones = milestones; this.sprints = sprints;
        this.sprintMemberships = sprintMemberships; this.tasks = tasks;
        this.metadata = metadata; this.dependencies = dependencies; this.baselines = baselines;
        this.calculator = calculator; this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public ProjectSchedulePage page(UUID ownerId, UUID projectId, UUID selectedBaselineId,
            ScheduleChangePreview preview, String notice) {
        Loaded loaded = load(ownerId, projectId);
        Map<UUID, ScheduleBaselineSnapshot> snapshots = new LinkedHashMap<>();
        List<ScheduleBaselineView> baselineViews = new ArrayList<>();
        for (ScheduleBaseline baseline : baselines.findAllByOwnerIdAndProjectIdOrderByCreatedAtDesc(ownerId, projectId)) {
            ScheduleBaselineSnapshot snapshot = readSnapshot(baseline);
            snapshots.put(baseline.getId(), snapshot);
            baselineViews.add(ScheduleBaselineView.from(baseline, snapshot));
        }
        ScheduleBaselineSnapshot selected = selectedBaselineId == null ? null : snapshots.get(selectedBaselineId);
        if (selectedBaselineId != null && selected == null) throw new TaskNotFoundException();
        return page(loaded, baselineViews, selectedBaselineId, selected, preview, notice);
    }

    @Transactional(readOnly = true)
    public List<DependencyLine> dependenciesForTask(UUID ownerId, UUID projectId, UUID taskId) {
        placements.findPlacement(taskId, ownerId, projectId).orElseThrow(TaskNotFoundException::new);
        Loaded loaded = load(ownerId, projectId);
        Map<UUID, String> titles = loaded.board().stream().collect(Collectors.toMap(
                item -> item.getTaskId(), item -> item.getTask().getTitle()));
        return loaded.edges().stream()
                .filter(edge -> edge.getPredecessorTaskId().equals(taskId) || edge.getSuccessorTaskId().equals(taskId))
                .map(edge -> new DependencyLine(edge.getId(), edge.getPredecessorTaskId(), edge.getSuccessorTaskId(),
                        titles.get(edge.getPredecessorTaskId()), titles.get(edge.getSuccessorTaskId()),
                        edge.getType(), edge.getLagDays(), false))
                .toList();
    }

    @Transactional
    public void addDependency(UUID ownerId, UUID projectId, UUID predecessorId, UUID successorId,
            TaskDependencyType type, int lagDays) {
        Project project = lockWritableProject(ownerId, projectId);
        if (type == null) throw new TaskRuleException("Choose a dependency type.");
        if (predecessorId == null || successorId == null || predecessorId.equals(successorId))
            throw new TaskRuleException("Choose two different tasks in this project.");
        if (lagDays < 0 || lagDays > 36500)
            throw new TaskRuleException("Lag must be between 0 and 36500 calendar days.");
        Task predecessor = placements.findPlacement(predecessorId, ownerId, projectId)
                .orElseThrow(TaskNotFoundException::new).getTask();
        Task successor = placements.findPlacement(successorId, ownerId, projectId)
                .orElseThrow(TaskNotFoundException::new).getTask();
        if (dependencies.existsByOwnerIdAndProjectIdAndPredecessorTaskIdAndSuccessorTaskId(
                ownerId, projectId, predecessorId, successorId))
            throw new TaskRuleException("A dependency already connects these tasks.");

        Loaded loaded = load(ownerId, projectId);
        List<ScheduleDependencyInput> proposed = new ArrayList<>(dependencyInputs(loaded.edges()));
        proposed.add(new ScheduleDependencyInput(UUID.randomUUID(), predecessorId, successorId, type, lagDays));
        try {
            calculator.topologicalOrder(loaded.board().stream().map(item -> item.getTask().getId()).collect(Collectors.toSet()), proposed);
        } catch (DependencyCycleException cycle) {
            String names = cycle.getInvolvedTaskIds().stream().map(id -> loaded.board().stream()
                    .filter(item -> item.getTask().getId().equals(id)).map(item -> item.getTask().getTitle())
                    .findFirst().orElse(id.toString())).collect(Collectors.joining(", "));
            throw new TaskRuleException("This dependency creates a circular schedule involving: " + names + ".");
        }
        dependencies.saveAndFlush(TaskDependency.create(ownerId, projectId, predecessorId, successorId, type, lagDays, Instant.now()));
        project.touchSchedule(Instant.now()); projects.flush();
    }

    @Transactional
    public void removeDependency(UUID ownerId, UUID projectId, UUID dependencyId) {
        Project project = lockWritableProject(ownerId, projectId);
        TaskDependency dependency = dependencies.findByIdAndOwnerIdAndProjectId(dependencyId, ownerId, projectId)
                .orElseThrow(TaskNotFoundException::new);
        dependencies.delete(dependency); dependencies.flush();
        project.touchSchedule(Instant.now()); projects.flush();
    }

    @Transactional(readOnly = true)
    public ScheduleChangePreview preview(UUID ownerId, UUID projectId, UUID taskId, int proposedDurationDays,
            LocalDate proposedStartDate, long expectedProjectVersion, long expectedTaskVersion) {
        if (proposedDurationDays < 1 || proposedDurationDays > 36500)
            throw new TaskRuleException("Duration must be between 1 and 36500 calendar days.");
        Loaded loaded = load(ownerId, projectId);
        if (loaded.project().getVersion() != expectedProjectVersion)
            throw new TaskRuleException("The project schedule changed. Reload the timeline and try again.");
        ProjectTaskPlacement placement = loaded.board().stream().filter(item -> item.getTaskId().equals(taskId))
                .findFirst().orElseThrow(TaskNotFoundException::new);
        Task task = placement.getTask();
        if (task.getVersion() != expectedTaskVersion)
            throw new TaskRuleException("This task changed. Reload the timeline and try again.");
        validatePlannedStart(loaded.project(), task, proposedStartDate);
        ScheduleResult current = calculation(loaded);
        ScheduleResult proposed = calculator.calculate(loaded.project().getStartDate(),
                taskInputs(loaded, taskId, proposedDurationDays, proposedStartDate), dependencyInputs(loaded.edges()));
        List<ScheduleImpactLine> impacts = impacts(loaded, current, proposed, null, taskId, proposedDurationDays);
        String message = proposed.status() == ScheduleResult.Status.READY ? null
                : "This estimate can be saved, but the project start date and every task duration are required for a critical path.";
        return new ScheduleChangePreview(taskId, task.getTitle(), proposedDurationDays, proposedStartDate,
                loaded.project().getVersion(), task.getVersion(), current, proposed, impacts, true, message);
    }

    @Transactional
    public void apply(UUID ownerId, UUID projectId, UUID taskId, int durationDays, LocalDate startDate,
            long expectedProjectVersion, long expectedTaskVersion) {
        if (durationDays < 1 || durationDays > 36500)
            throw new TaskRuleException("Duration must be between 1 and 36500 calendar days.");
        Project project = lockWritableProject(ownerId, projectId);
        if (project.getVersion() != expectedProjectVersion)
            throw new TaskRuleException("The project schedule changed after preview. Review the updated timeline before saving.");
        Task task = tasks.findLockedByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
        ProjectTaskPlacement placement = placements.findPlacement(taskId, ownerId, projectId).orElseThrow(TaskNotFoundException::new);
        if (task.getVersion() != expectedTaskVersion)
            throw new TaskRuleException("This task changed after preview. Review the updated timeline before saving.");
        validatePlannedStart(project, task, startDate);
        Instant now = Instant.now();
        TaskScheduleMetadata value = metadata.findByTaskIdAndOwnerId(taskId, ownerId)
                .orElseGet(() -> TaskScheduleMetadata.create(ownerId, taskId, durationDays, now));
        value.setDuration(durationDays, now); metadata.save(value);
        task.updatePlannedStartDate(startDate, now);
        project.touchSchedule(now);
        projects.flush();
    }

    @Transactional
    public UUID createBaseline(UUID ownerId, UUID projectId, String name) {
        Project project = lockWritableProject(ownerId, projectId);
        if (name == null || name.isBlank() || name.trim().length() > 100)
            throw new TaskRuleException("Enter a baseline name up to 100 characters.");
        Loaded loaded = load(ownerId, projectId);
        ScheduleBaselineSnapshot snapshot = snapshot(loaded);
        try {
            ScheduleBaseline baseline = ScheduleBaseline.create(ownerId, projectId, name, objectMapper.writeValueAsString(snapshot), Instant.now());
            return baselines.save(baseline).getId();
        } catch (JacksonException exception) {
            throw new TaskRuleException("The schedule snapshot could not be saved.");
        }
    }

    private ProjectSchedulePage page(Loaded loaded, List<ScheduleBaselineView> baselineViews, UUID selectedBaselineId,
            ScheduleBaselineSnapshot selected, ScheduleChangePreview preview, String notice) {
        ScheduleResult result = calculation(loaded);
        Map<UUID, Integer> durationByTask = loaded.durations();
        Map<UUID, TaskScheduleResult> calculated = result.taskResults();
        Map<UUID, ScheduleBaselineSnapshot.Task> baselineTasks = selected == null ? Map.of()
                : selected.tasks().stream().collect(Collectors.toMap(ScheduleBaselineSnapshot.Task::id, Function.identity(),
                        (left, right) -> left));
        long axisDays = axisDays(loaded, result, baselineTasks);
        List<TaskScheduleLine> taskLines = new ArrayList<>();
        Map<UUID, String> titles = new HashMap<>();
        for (ProjectTaskPlacement placement : loaded.board()) {
            Task task = placement.getTask(); titles.put(task.getId(), task.getTitle());
            TaskScheduleResult calculation = calculated.get(task.getId());
            Integer duration = durationByTask.get(task.getId());
            ScheduleBaselineSnapshot.Task baseline = baselineTasks.get(task.getId());
            Double baselineStart = baseline == null || baseline.calculation() == null || loaded.project().getStartDate() == null
                    ? null : percentFromDate(loaded.project().getStartDate(), baseline.calculation().earliestStart(), axisDays);
            Double baselineWidth = baseline == null || baseline.durationDays() == null
                    ? null : 100.0 * Math.max(1, baseline.durationDays()) / axisDays;
            taskLines.add(new TaskScheduleLine(task.getId(), task.getTitle(), task.getStatus(), task.getPriority(),
                    task.getStartDate(), task.getDueDate(), duration, task.getVersion(), task.getCompletedAt(),
                    placement.getMilestoneId(), placement.getMilestone() == null ? null : placement.getMilestone().getTitle(),
                    sprintName(loaded.sprintByTask().get(task.getId())),
                    loaded.sprintByTask().containsKey(task.getId()) ? loaded.sprintByTask().get(task.getId()).getStartDate() : null,
                    loaded.sprintByTask().containsKey(task.getId()) ? loaded.sprintByTask().get(task.getId()).getEndDate() : null,
                    loaded.sprintByTask().containsKey(task.getId()) ? loaded.sprintByTask().get(task.getId()).getGoal() : null,
                    calculation, calculation == null ? null : 100.0 * calculation.earliestStartOffset() / axisDays,
                    calculation == null ? null : 100.0 * Math.max(1, duration) / axisDays, baselineStart, baselineWidth));
        }
        List<DependencyLine> dependencyLines = loaded.edges().stream().map(edge -> new DependencyLine(edge.getId(),
                edge.getPredecessorTaskId(), edge.getSuccessorTaskId(), titles.get(edge.getPredecessorTaskId()),
                titles.get(edge.getSuccessorTaskId()), edge.getType(), edge.getLagDays(),
                result.criticalDependencyIds().contains(edge.getId()))).toList();
        List<MilestoneLine> milestoneLines = loaded.milestones().stream().map(milestone -> new MilestoneLine(
                milestone.getId(), milestone.getTitle(), milestone.getStatus(), milestone.getTargetDate(),
                milestone.getCompletedAt(), markerPercent(loaded.project().getStartDate(), milestone.getTargetDate(), axisDays))).toList();
        List<ScheduleImpactLine> comparison = selected == null ? List.of() : impacts(loaded, result, result, selected, null, null);
        List<DependencyGraphNode> graphNodes = graphNodes(loaded, result);
        int graphHeight = graphNodes.stream().mapToInt(node -> node.topPixels() + 90).max().orElse(140);
        return new ProjectSchedulePage(loaded.project(), result, taskLines, dependencyLines, graphNodes, graphHeight, milestoneLines,
                baselineViews, comparison, selectedBaselineId, axisDays, ticks(loaded.project().getStartDate(), axisDays), preview, notice);
    }

    private List<ScheduleImpactLine> impacts(Loaded loaded, ScheduleResult before, ScheduleResult after,
            ScheduleBaselineSnapshot baseline, UUID changedTaskId, Integer proposedDuration) {
        Map<UUID, String> titleById = loaded.board().stream().collect(Collectors.toMap(
                placement -> placement.getTask().getId(), placement -> placement.getTask().getTitle()));
        Map<UUID, ScheduleBaselineSnapshot.Task> baselineById = baseline == null ? Map.of()
                : baseline.tasks().stream().collect(Collectors.toMap(ScheduleBaselineSnapshot.Task::id, Function.identity(), (a, b) -> a));
        List<ScheduleImpactLine> result = new ArrayList<>();
        for (UUID id : titleById.keySet()) {
            TaskScheduleResult oldResult = baseline == null ? before.taskResults().get(id)
                    : (baselineById.get(id) == null ? null : baselineById.get(id).calculation());
            TaskScheduleResult newResult = after.taskResults().get(id);
            Integer oldDuration = baseline == null ? loaded.durations().get(id)
                    : (baselineById.get(id) == null ? null : baselineById.get(id).durationDays());
            Integer newDuration = id.equals(changedTaskId) ? proposedDuration : loaded.durations().get(id);
            LocalDate oldStart = oldResult == null ? (baseline == null ? null : baselineById.get(id) == null ? null : baselineById.get(id).plannedStart())
                    : oldResult.earliestStart();
            LocalDate newStart = newResult == null ? null : newResult.earliestStart();
            LocalDate oldFinish = oldResult == null ? null : oldResult.earliestFinish();
            LocalDate newFinish = newResult == null ? null : newResult.earliestFinish();
            long floatChange = oldResult == null || newResult == null ? 0 : newResult.totalFloatDays() - oldResult.totalFloatDays();
            Integer durationChange = oldDuration == null || newDuration == null ? null : newDuration - oldDuration;
            boolean critical = newResult != null && newResult.critical();
            if (baseline != null || !java.util.Objects.equals(oldStart, newStart)
                    || !java.util.Objects.equals(oldFinish, newFinish) || !java.util.Objects.equals(oldDuration, newDuration)
                    || floatChange != 0 || critical)
                result.add(new ScheduleImpactLine(id, titleById.get(id), oldStart, newStart, oldFinish, newFinish,
                        durationChange, floatChange, critical));
        }
        result.sort(Comparator.comparing(ScheduleImpactLine::title, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(result);
    }

    private long axisDays(Loaded loaded, ScheduleResult result, Map<UUID, ScheduleBaselineSnapshot.Task> baseline) {
        long max = Math.max(1, result.status() == ScheduleResult.Status.READY ? result.projectDurationDays() : 0);
        LocalDate origin = loaded.project().getStartDate();
        if (origin != null) {
            for (ProjectTaskPlacement item : loaded.board()) {
                if (item.getTask().getStartDate() != null) max = Math.max(max, Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(origin, item.getTask().getStartDate())) + 1);
                if (item.getTask().getDueDate() != null) max = Math.max(max, Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(origin, item.getTask().getDueDate())) + 1);
            }
            for (ProjectMilestone item : loaded.milestones()) if (item.getTargetDate() != null)
                max = Math.max(max, Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(origin, item.getTargetDate())) + 1);
            for (ScheduleBaselineSnapshot.Task item : baseline.values()) if (item.calculation() != null)
                max = Math.max(max, Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(origin, item.calculation().earliestFinish())) + 1);
        }
        return Math.min(36500, max + Math.min(7, 36500 - max));
    }

    private List<TimelineTick> ticks(LocalDate origin, long axisDays) {
        if (origin == null) return List.of();
        List<TimelineTick> result = new ArrayList<>();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MMM uuuu");
        LocalDate tick = origin;
        result.add(new TimelineTick(tick, formatter.format(tick), 0));
        tick = origin.withDayOfMonth(1).plusMonths(1);
        while (java.time.temporal.ChronoUnit.DAYS.between(origin, tick) < axisDays) {
            double position = 100.0 * java.time.temporal.ChronoUnit.DAYS.between(origin, tick) / axisDays;
            result.add(new TimelineTick(tick, formatter.format(tick), position));
            tick = tick.plusMonths(1);
        }
        return result;
    }

    private List<DependencyGraphNode> graphNodes(Loaded loaded, ScheduleResult result) {
        List<UUID> ids = loaded.board().stream().map(item -> item.getTask().getId()).toList();
        if (ids.isEmpty()) return List.of();
        List<ScheduleDependencyInput> edges = dependencyInputs(loaded.edges());
        List<UUID> order = calculator.topologicalOrder(new HashSet<>(ids), edges);
        Map<UUID, Integer> depth = new HashMap<>();
        Map<UUID, List<UUID>> outgoing = new HashMap<>();
        for (ScheduleDependencyInput edge : edges)
            outgoing.computeIfAbsent(edge.predecessorId(), ignored -> new ArrayList<>()).add(edge.successorId());
        for (UUID id : order) {
            int currentDepth = depth.getOrDefault(id, 0);
            for (UUID next : outgoing.getOrDefault(id, List.of())) depth.merge(next, currentDepth + 1, Math::max);
        }
        Map<UUID, ProjectTaskPlacement> byId = loaded.board().stream()
                .collect(Collectors.toMap(ProjectTaskPlacement::getTaskId, Function.identity()));
        Map<Integer, List<UUID>> columns = new TreeMap<>();
        for (UUID id : order) columns.computeIfAbsent(depth.getOrDefault(id, 0), ignored -> new ArrayList<>()).add(id);
        columns.values().forEach(column -> column.sort(Comparator
                .comparing((UUID id) -> byId.get(id).getTask().getTitle(), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(UUID::toString)));
        int lastDepth = depth.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<DependencyGraphNode> nodes = new ArrayList<>();
        for (Map.Entry<Integer, List<UUID>> column : columns.entrySet()) {
            for (int row = 0; row < column.getValue().size(); row++) {
                UUID id = column.getValue().get(row);
                Task task = byId.get(id).getTask();
                TaskScheduleResult scheduled = result.taskResults().get(id);
                double position = lastDepth == 0 ? 50 : 10 + 80.0 * column.getKey() / lastDepth;
                nodes.add(new DependencyGraphNode(id, task.getTitle(), task.getStatus().name(),
                        loaded.durations().get(id), scheduled != null && scheduled.critical(), position, 22 + row * 94));
            }
        }
        return List.copyOf(nodes);
    }

    private String sprintName(Sprint sprint) { return sprint == null ? null : sprint.getName(); }

    private Double markerPercent(LocalDate origin, LocalDate date, long axisDays) {
        return origin == null || date == null ? null : percentFromDate(origin, date, axisDays);
    }

    private static double percentFromDate(LocalDate origin, LocalDate date, long axisDays) {
        long offset = Math.max(0, java.time.temporal.ChronoUnit.DAYS.between(origin, date));
        return 100.0 * Math.min(offset, axisDays) / axisDays;
    }

    private ScheduleBaselineSnapshot snapshot(Loaded loaded) {
        ScheduleResult result = calculation(loaded);
        List<ScheduleBaselineSnapshot.Task> taskSnapshots = loaded.board().stream().map(item -> {
            Task task = item.getTask();
            return new ScheduleBaselineSnapshot.Task(task.getId(), task.getTitle(), task.getStatus().name(),
                    task.getStartDate(), task.getDueDate(), loaded.durations().get(task.getId()), result.taskResults().get(task.getId()));
        }).toList();
        List<ScheduleBaselineSnapshot.Dependency> edgeSnapshots = loaded.edges().stream().map(edge ->
                new ScheduleBaselineSnapshot.Dependency(edge.getId(), edge.getPredecessorTaskId(), edge.getSuccessorTaskId(),
                        edge.getType(), edge.getLagDays())).toList();
        List<ScheduleBaselineSnapshot.Milestone> milestoneSnapshots = loaded.milestones().stream().map(item ->
                new ScheduleBaselineSnapshot.Milestone(item.getId(), item.getTitle(), item.getStatus().name(),
                        item.getTargetDate(), item.getCompletedAt())).toList();
        return new ScheduleBaselineSnapshot(1, loaded.project().getStartDate(), result,
                taskSnapshots, edgeSnapshots, milestoneSnapshots);
    }

    private ScheduleBaselineSnapshot readSnapshot(ScheduleBaseline baseline) {
        try { return objectMapper.readValue(baseline.getSnapshotJson(), ScheduleBaselineSnapshot.class); }
        catch (JacksonException exception) { throw new TaskRuleException("A saved schedule baseline could not be read."); }
    }

    private Loaded load(UUID ownerId, UUID projectId) {
        Project project = projects.findByIdAndOwner_IdIncludingShared(projectId, ownerId).orElseThrow(TaskNotFoundException::new);
        List<ProjectTaskPlacement> board = placements.findBoard(ownerId, projectId);
        List<UUID> taskIds = board.stream().map(ProjectTaskPlacement::getTaskId).toList();
        Map<UUID, Integer> durations = taskIds.isEmpty() ? Map.of() : metadata.findAllByOwnerIdAndTaskIdIn(ownerId, taskIds)
                .stream().collect(Collectors.toMap(TaskScheduleMetadata::getTaskId, TaskScheduleMetadata::getPlannedDurationDays));
        Map<UUID, Sprint> sprintById = sprints.findAllByProjectIdOrderByStartDateDesc(projectId).stream()
                .filter(sprint -> sprint.getStatus() == SprintStatus.PLANNED || sprint.getStatus() == SprintStatus.ACTIVE)
                .collect(Collectors.toMap(Sprint::getId, Function.identity(), (first, ignored) -> first));
        Map<UUID, Sprint> sprintByTask = new HashMap<>();
        for (SprintMembership membership : sprintMemberships.findScheduledProjectMemberships(projectId,
                List.of(SprintMembershipStatus.PLANNED, SprintMembershipStatus.ACTIVE),
                List.of(SprintStatus.PLANNED, SprintStatus.ACTIVE))) {
            Sprint sprint = sprintById.get(membership.getSprintId());
            if (sprint != null) sprintByTask.putIfAbsent(membership.getTaskId(), sprint);
        }
        return new Loaded(project, board, durations,
                dependencies.findAllByOwnerIdAndProjectIdOrderByCreatedAtAsc(ownerId, projectId),
                milestones.findAllByProjectIdOrderByPositionAsc(projectId), sprintByTask);
    }

    private ScheduleResult calculation(Loaded loaded) {
        return calculator.calculate(loaded.project().getStartDate(), taskInputs(loaded, null, null, null), dependencyInputs(loaded.edges()));
    }

    private List<ScheduleTaskInput> taskInputs(Loaded loaded, UUID changedTask, Integer duration, LocalDate startDate) {
        return loaded.board().stream().map(item -> {
            Task task = item.getTask();
            boolean changed = task.getId().equals(changedTask);
            return new ScheduleTaskInput(task.getId(), task.getTitle(), changed ? duration : loaded.durations().get(task.getId()),
                    changed ? startDate : task.getStartDate());
        }).toList();
    }

    private List<ScheduleDependencyInput> dependencyInputs(List<TaskDependency> edges) {
        return edges.stream().map(edge -> new ScheduleDependencyInput(edge.getId(), edge.getPredecessorTaskId(),
                edge.getSuccessorTaskId(), edge.getType(), edge.getLagDays())).toList();
    }

    private Project lockWritableProject(UUID ownerId, UUID projectId) {
        Project project = projects.findLockedByIdAndOwnerIncludingShared(projectId, ownerId).orElseThrow(TaskNotFoundException::new);
        if (project.getArchivedAt() != null) throw new TaskRuleException("Restore this project before changing its schedule.");
        return project;
    }

    private void validatePlannedStart(Project project, Task task, LocalDate startDate) {
        if (startDate != null && project.getStartDate() != null && startDate.isBefore(project.getStartDate()))
            throw new TaskRuleException("The planned start date cannot be before the project start date.");
        if (startDate != null && task.getDueDate() != null && startDate.isAfter(task.getDueDate()))
            throw new TaskRuleException("The planned start date cannot be after the task due date.");
    }

    private record Loaded(Project project, List<ProjectTaskPlacement> board, Map<UUID, Integer> durations,
            List<TaskDependency> edges, List<ProjectMilestone> milestones, Map<UUID, Sprint> sprintByTask) { }
}
