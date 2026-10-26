package com.taskos.agile.service;

import com.taskos.agile.dto.AgileBacklogItem;
import com.taskos.agile.dto.BurndownPoint;
import com.taskos.agile.dto.EpicProgress;
import com.taskos.agile.dto.SprintBoardColumn;
import com.taskos.agile.dto.SprintMetrics;
import com.taskos.agile.entity.*;
import com.taskos.agile.repository.*;
import com.taskos.project.entity.BoardColumn;
import com.taskos.project.entity.BoardStage;
import com.taskos.project.entity.Project;
import com.taskos.project.repository.BoardColumnRepository;
import com.taskos.project.repository.ProjectRepository;
import com.taskos.project.repository.ProjectTaskPlacementRepository;
import com.taskos.project.service.ProjectService;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.event.TaskStatusChangedEvent;
import com.taskos.task.service.TaskNotFoundException;
import com.taskos.task.service.TaskRuleException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional
public class AgileService {
    private static final List<SprintMembershipStatus> CURRENT_MEMBERSHIPS =
            List.of(SprintMembershipStatus.PLANNED, SprintMembershipStatus.ACTIVE);
    private static final List<SprintStatus> CURRENT_SPRINTS = List.of(SprintStatus.PLANNED, SprintStatus.ACTIVE);
    private static final int MAX_CRITERIA = 20;
    private static final int MAX_SPRINT_DAYS = 90;

    private final ProjectRepository projects;
    private final ProjectTaskPlacementRepository placements;
    private final BoardColumnRepository columns;
    private final ProjectService projectService;
    private final AgileProjectSettingsRepository settings;
    private final AgileWorkItemRepository workItems;
    private final EpicRepository epics;
    private final AcceptanceCriterionRepository criteria;
    private final SprintRepository sprints;
    private final SprintMembershipRepository memberships;
    private final SprintBaselineItemRepository baselines;
    private final SprintEventRepository events;
    private final SprintCeremonyRepository ceremonies;
    private final SprintDailyNoteRepository dailyNotes;
    private final SprintImpedimentRepository impediments;
    private final SprintActionItemRepository actionItems;
    private final DefinitionOfDoneCriterionRepository definitionOfDoneCriteria;

    public AgileService(ProjectRepository projects, ProjectTaskPlacementRepository placements,
            BoardColumnRepository columns, ProjectService projectService,
            AgileProjectSettingsRepository settings, AgileWorkItemRepository workItems, EpicRepository epics,
            AcceptanceCriterionRepository criteria, SprintRepository sprints,
            SprintMembershipRepository memberships, SprintBaselineItemRepository baselines,
            SprintEventRepository events, SprintCeremonyRepository ceremonies,
            SprintDailyNoteRepository dailyNotes, SprintImpedimentRepository impediments,
            SprintActionItemRepository actionItems, DefinitionOfDoneCriterionRepository definitionOfDoneCriteria) {
        this.projects = projects; this.placements = placements; this.columns = columns;
        this.projectService = projectService; this.settings = settings; this.workItems = workItems;
        this.epics = epics; this.criteria = criteria; this.sprints = sprints; this.memberships = memberships;
        this.baselines = baselines; this.events = events; this.ceremonies = ceremonies;
        this.dailyNotes = dailyNotes; this.impediments = impediments; this.actionItems = actionItems;
        this.definitionOfDoneCriteria = definitionOfDoneCriteria;
    }

    public AgileProjectSettings configure(UUID ownerId, UUID projectId, boolean enabled, String definitionOfDone) {
        Project project = lockProject(ownerId, projectId);
        writable(project);
        if (definitionOfDone != null && definitionOfDone.length() > 4000)
            throw new TaskRuleException("The project Definition of Done must be 4,000 characters or fewer.");
        if (definitionOfDone != null && definitionOfDone.lines().filter(line -> !line.isBlank()).count() > MAX_CRITERIA)
            throw new TaskRuleException("The project Definition of Done can contain up to 20 checklist items.");
        if (definitionOfDone != null && definitionOfDone.lines().anyMatch(line -> line.trim().length() > 500))
            throw new TaskRuleException("Each Definition of Done checklist item must be 500 characters or fewer.");
        AgileProjectSettings config = settings.findById(projectId).orElseGet(() -> AgileProjectSettings.create(projectId));
        boolean newlyEnabled = enabled && !config.isEnabled();
        config.configure(enabled, definitionOfDone);
        config = settings.saveAndFlush(config);
        if (newlyEnabled) {
            initializeItems(ownerId, projectId);
            event(ownerId, projectId, null, null, SprintEventType.SCRUM_ENABLED, "Scrum enabled for the project", null, null, null);
        }
        return config;
    }

    public AgileProjectSettings projectSettings(UUID ownerId, UUID projectId) {
        project(ownerId, projectId);
        return settings.findById(projectId).orElse(null);
    }

    public UUID createWorkItem(UUID ownerId, UUID projectId, String title, String description,
            TaskPriority priority, LocalDate dueDate) {
        return createWorkItem(ownerId, ownerId, projectId, title, description, priority, dueDate);
    }

    public UUID createWorkItem(UUID actorId, UUID ownerId, UUID projectId, String title, String description,
            TaskPriority priority, LocalDate dueDate) {
        Project project = lockProject(ownerId, projectId);
        writable(project); requireEnabled(projectId);
        BoardColumn backlogColumn = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId).stream()
                .filter(column -> column.getStage() == BoardStage.BACKLOG).findFirst()
                .orElseGet(() -> columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId).stream()
                        .filter(column -> column.getStage() == BoardStage.TODO).findFirst().orElseThrow(TaskNotFoundException::new));
        UUID taskId = project.getWorkspaceId() == null
                ? projectService.createTask(ownerId, projectId, backlogColumn.getId(), title, description, priority, dueDate)
                : projectService.createSharedProjectTask(actorId, projectId, backlogColumn.getId(), title, description, priority, dueDate);
        initializeItems(ownerId, projectId);
        return taskId;
    }

    public List<AgileBacklogItem> backlog(UUID ownerId, UUID projectId, String search, AgileWorkItemType type,
            TaskPriority priority, UUID epicId, TaskStatus status, String sprintFilter, boolean unestimated) {
        project(ownerId, projectId); requireEnabled(projectId); initializeItems(ownerId, projectId);
        String query = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        String normalizedQuery = query.length() > 100 ? query.substring(0, 100) : query;
        Predicate<AgileBacklogItem> filter = item -> (normalizedQuery.isEmpty()
                || item.task().getTitle().toLowerCase(Locale.ROOT).contains(normalizedQuery)
                || item.task().getDescription().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                && (type == null || item.agile().getType() == type)
                && (priority == null || item.task().getPriority() == priority)
                && (epicId == null || Objects.equals(item.agile().getEpicId(), epicId))
                && (status == null || item.task().getStatus() == status)
                && (!unestimated || item.agile().getStoryPoints() == null)
                && (sprintFilter == null || sprintFilter.equals("ALL")
                    || (sprintFilter.equals("BACKLOG") && item.sprint() == null)
                    || (sprintFilter.equals("IN_SPRINT") && item.sprint() != null));
        return backlogItems(ownerId, projectId).stream().filter(filter).toList();
    }

    public List<AgileBacklogItem> backlogItems(UUID ownerId, UUID projectId) {
        project(ownerId, projectId); requireEnabled(projectId); initializeItems(ownerId, projectId);
        List<com.taskos.project.entity.ProjectTaskPlacement> board = placements.findBoard(ownerId, projectId);
        Map<UUID, com.taskos.project.entity.ProjectTaskPlacement> placementByTask = board.stream()
                .collect(Collectors.toMap(com.taskos.project.entity.ProjectTaskPlacement::getTaskId, item -> item));
        Map<UUID, AgileWorkItem> agileByTask = workItems.findAllByProjectIdOrderByBacklogPositionAsc(projectId).stream()
                .collect(Collectors.toMap(AgileWorkItem::getTaskId, item -> item));
        Map<UUID, Epic> epicById = epics.findAllByProjectIdOrderByCreatedAtDesc(projectId).stream()
                .collect(Collectors.toMap(Epic::getId, item -> item));
        Map<UUID, Sprint> sprintById = sprints.findAllByProjectIdOrderByStartDateDesc(projectId).stream()
                .collect(Collectors.toMap(Sprint::getId, item -> item));
        List<UUID> currentSprintIds = sprintById.values().stream().filter(item -> CURRENT_SPRINTS.contains(item.getStatus()))
                .map(Sprint::getId).toList();
        Map<UUID, Sprint> assignedSprint = new HashMap<>();
        if (!currentSprintIds.isEmpty()) {
            memberships.findAllBySprintIdInOrderByPositionAsc(currentSprintIds).stream()
                    .filter(item -> CURRENT_MEMBERSHIPS.contains(item.getStatus()))
                    .forEach(item -> assignedSprint.put(item.getTaskId(), sprintById.get(item.getSprintId())));
        }
        List<UUID> taskIds = board.stream().map(com.taskos.project.entity.ProjectTaskPlacement::getTaskId).toList();
        Map<UUID, List<AcceptanceCriterion>> criteriaByTask = taskIds.isEmpty() ? Map.of() :
                criteria.findAllByTaskIdInOrderByPositionAsc(taskIds).stream()
                        .collect(Collectors.groupingBy(AcceptanceCriterion::getTaskId));
        Map<UUID, List<DefinitionOfDoneCriterion>> definitionByTask = taskIds.isEmpty() ? Map.of() :
                definitionOfDoneCriteria.findAllByTaskIdInOrderByPositionAsc(taskIds).stream()
                        .collect(Collectors.groupingBy(DefinitionOfDoneCriterion::getTaskId));
        return workItems.findAllByProjectIdOrderByBacklogPositionAsc(projectId).stream()
                .map(item -> {
                    var placement = placementByTask.get(item.getTaskId());
                    if (placement == null) return null;
                    return new AgileBacklogItem(placement.getTask(), item, epicById.get(item.getEpicId()),
                            assignedSprint.get(item.getTaskId()), criteriaByTask.getOrDefault(item.getTaskId(), List.of()),
                            definitionByTask.getOrDefault(item.getTaskId(), List.of()),
                            placement.getColumn());
                }).filter(Objects::nonNull).toList();
    }

    public void updateWorkItem(UUID ownerId, UUID projectId, UUID taskId, AgileWorkItemType type,
            Integer points, UUID epicId, String definitionOfDone) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        ensureProjectTask(ownerId, projectId, taskId);
        if (type == null) throw new TaskRuleException("Choose a work item type.");
        validatePoints(points);
        if (definitionOfDone != null && definitionOfDone.length() > 2000)
            throw new TaskRuleException("The task Definition of Done must be 2,000 characters or fewer.");
        Epic epic = epicId == null ? null : epics.findByIdAndProjectId(epicId, projectId).orElseThrow(TaskNotFoundException::new);
        if (epic != null && epic.getArchivedAt() != null) throw new TaskRuleException("Restore the epic before assigning work to it.");
        AgileWorkItem item = workItems.findById(taskId).orElseThrow(TaskNotFoundException::new);
        Integer oldPoints = item.getStoryPoints();
        Optional<SprintMembership> active = memberships.findCurrentOtherMembership(taskId,
                List.of(SprintMembershipStatus.ACTIVE), List.of(SprintStatus.ACTIVE), null);
        item.update(type, points, epicId, definitionOfDone);
        workItems.save(item);
        if (!Objects.equals(oldPoints, points) && active.isPresent()) {
            SprintMembership membership = active.get();
            Sprint sprint = sprint(ownerId, projectId, membership.getSprintId());
            event(ownerId, projectId, sprint.getId(), taskId, SprintEventType.ESTIMATE_CHANGED,
                    "Story point estimate changed", pointValue(oldPoints), pointValue(points), null);
        }
        event(ownerId, projectId, null, taskId, SprintEventType.BACKLOG_ITEM_UPDATED,
                "Backlog item updated", null, type.name(), null);
    }

    public void reorderBacklog(UUID ownerId, UUID projectId, UUID taskId, int direction) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        if (direction != -1 && direction != 1) throw new TaskRuleException("Choose a valid reorder direction.");
        List<AgileWorkItem> ordered = new ArrayList<>(workItems.findAllByProjectIdOrderByBacklogPositionAsc(projectId));
        AgileWorkItem selected = ordered.stream().filter(item -> item.getTaskId().equals(taskId)).findFirst().orElseThrow(TaskNotFoundException::new);
        int to = ordered.indexOf(selected) + direction;
        if (to < 0 || to >= ordered.size()) return;
        Collections.swap(ordered, ordered.indexOf(selected), to);
        normalizeBacklogOrder(ordered);
        event(ownerId, projectId, null, taskId, SprintEventType.BACKLOG_REORDERED, "Product backlog order changed", null, null, null);
    }

    public void reorderBacklog(UUID ownerId, UUID projectId, List<UUID> taskIds) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        List<AgileWorkItem> current = new ArrayList<>(workItems.findAllByProjectIdOrderByBacklogPositionAsc(projectId));
        if (taskIds == null || taskIds.size() != current.size() || new HashSet<>(taskIds).size() != taskIds.size()
                || !new HashSet<>(taskIds).equals(current.stream().map(AgileWorkItem::getTaskId).collect(Collectors.toSet())))
            throw new TaskRuleException("The backlog changed in another tab. Reload it before saving this order.");
        Map<UUID, AgileWorkItem> byId = current.stream().collect(Collectors.toMap(AgileWorkItem::getTaskId, item -> item));
        List<AgileWorkItem> ordered = taskIds.stream().map(byId::get).toList();
        normalizeBacklogOrder(ordered);
        event(ownerId, projectId, null, null, SprintEventType.BACKLOG_REORDERED, "Product backlog reordered", null, null, null);
    }

    public AcceptanceCriterion addCriterion(UUID ownerId, UUID projectId, UUID taskId, String criterion) {
        lockProject(ownerId, projectId); requireEnabled(projectId); ensureProjectTask(ownerId, projectId, taskId);
        if (criterion == null || criterion.isBlank() || criterion.trim().length() > 500)
            throw new TaskRuleException("Enter an acceptance criterion of 1 to 500 characters.");
        if (criteria.countByTaskId(taskId) >= MAX_CRITERIA)
            throw new TaskRuleException("A work item can have up to 20 acceptance criteria.");
        AcceptanceCriterion saved = criteria.save(AcceptanceCriterion.create(taskId, criterion, (int) criteria.countByTaskId(taskId)));
        event(ownerId, projectId, null, taskId, SprintEventType.CRITERION_ADDED, "Acceptance criterion added", null, criterion.trim(), null);
        return saved;
    }

    public void addDefinitionOfDoneCriterion(UUID ownerId, UUID projectId, UUID taskId, String criterion) {
        lockProject(ownerId, projectId); requireEnabled(projectId); ensureProjectTask(ownerId, projectId, taskId);
        if (criterion == null || criterion.isBlank() || criterion.trim().length() > 500)
            throw new TaskRuleException("Enter a Definition of Done item of 1 to 500 characters.");
        if (definitionOfDoneCriteria.countByTaskId(taskId) >= MAX_CRITERIA)
            throw new TaskRuleException("A work item can have up to 20 Definition of Done items.");
        definitionOfDoneCriteria.save(DefinitionOfDoneCriterion.create(taskId, criterion,
                (int) definitionOfDoneCriteria.countByTaskId(taskId)));
    }

    public void satisfyDefinitionOfDoneCriterion(UUID ownerId, UUID projectId, UUID taskId, UUID criterionId, boolean satisfied) {
        lockProject(ownerId, projectId); requireEnabled(projectId); ensureProjectTask(ownerId, projectId, taskId);
        DefinitionOfDoneCriterion criterion = definitionOfDoneCriteria.findByIdAndTaskId(criterionId, taskId)
                .orElseThrow(TaskNotFoundException::new);
        criterion.satisfy(satisfied);
    }

    public void updateCriterion(UUID ownerId, UUID projectId, UUID taskId, UUID criterionId, String criterion) {
        lockProject(ownerId, projectId); requireEnabled(projectId); ensureProjectTask(ownerId, projectId, taskId);
        if (criterion == null || criterion.isBlank() || criterion.trim().length() > 500)
            throw new TaskRuleException("Enter an acceptance criterion of 1 to 500 characters.");
        AcceptanceCriterion selected = criteria.findByIdAndTaskId(criterionId, taskId).orElseThrow(TaskNotFoundException::new);
        String previous = selected.getCriterion(); selected.update(criterion); criteria.save(selected);
        event(ownerId, projectId, null, taskId, SprintEventType.CRITERION_UPDATED, "Acceptance criterion edited", previous, criterion.trim(), null);
    }

    public void satisfyCriterion(UUID ownerId, UUID projectId, UUID taskId, UUID criterionId, boolean satisfied) {
        lockProject(ownerId, projectId); requireEnabled(projectId); ensureProjectTask(ownerId, projectId, taskId);
        AcceptanceCriterion selected = criteria.findByIdAndTaskId(criterionId, taskId).orElseThrow(TaskNotFoundException::new);
        selected.setSatisfied(satisfied); criteria.save(selected);
        event(ownerId, projectId, null, taskId, SprintEventType.CRITERION_UPDATED,
                satisfied ? "Acceptance criterion marked satisfied" : "Acceptance criterion reopened", null, Boolean.toString(satisfied), null);
    }

    public void removeCriterion(UUID ownerId, UUID projectId, UUID taskId, UUID criterionId) {
        lockProject(ownerId, projectId); requireEnabled(projectId); ensureProjectTask(ownerId, projectId, taskId);
        AcceptanceCriterion selected = criteria.findByIdAndTaskId(criterionId, taskId).orElseThrow(TaskNotFoundException::new);
        criteria.delete(selected); criteria.flush();
        normalizeCriteria(taskId);
        event(ownerId, projectId, null, taskId, SprintEventType.CRITERION_REMOVED, "Acceptance criterion removed", selected.getCriterion(), null, null);
    }

    public void reorderCriterion(UUID ownerId, UUID projectId, UUID taskId, UUID criterionId, int direction) {
        lockProject(ownerId, projectId); requireEnabled(projectId); ensureProjectTask(ownerId, projectId, taskId);
        if (direction != -1 && direction != 1) throw new TaskRuleException("Choose a valid reorder direction.");
        List<AcceptanceCriterion> ordered = new ArrayList<>(criteria.findAllByTaskIdOrderByPositionAsc(taskId));
        AcceptanceCriterion selected = ordered.stream().filter(item -> item.getId().equals(criterionId)).findFirst().orElseThrow(TaskNotFoundException::new);
        int to = ordered.indexOf(selected) + direction;
        if (to < 0 || to >= ordered.size()) return;
        Collections.swap(ordered, ordered.indexOf(selected), to);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).moveTo(1000 + i);
        criteria.saveAllAndFlush(ordered);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).moveTo(i);
        criteria.saveAll(ordered);
    }

    public Epic createEpic(UUID ownerId, UUID projectId, String title, String description, LocalDate targetDate) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        validateEpic(title, description);
        Epic epic = epics.save(Epic.create(projectId, title, description, targetDate, Instant.now()));
        event(ownerId, projectId, null, null, SprintEventType.EPIC_CREATED, "Epic created: " + epic.getTitle(), null, null, null);
        return epic;
    }

    public void updateEpic(UUID ownerId, UUID projectId, UUID epicId, String title, String description, LocalDate targetDate) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId); validateEpic(title, description);
        Epic epic = epics.findByIdAndProjectId(epicId, projectId).orElseThrow(TaskNotFoundException::new);
        if (epic.getArchivedAt() != null) throw new TaskRuleException("Restore the epic before editing it.");
        String previous = epic.getTitle(); epic.update(title, description, targetDate, Instant.now());
        event(ownerId, projectId, null, null, SprintEventType.EPIC_UPDATED, "Epic updated: " + epic.getTitle(), previous, epic.getTitle(), null);
    }

    public void archiveEpic(UUID ownerId, UUID projectId, UUID epicId) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        Epic epic = epics.findByIdAndProjectId(epicId, projectId).orElseThrow(TaskNotFoundException::new);
        if (epic.getArchivedAt() == null) {
            epic.archive(Instant.now()); event(ownerId, projectId, null, null, SprintEventType.EPIC_ARCHIVED,
                    "Epic archived: " + epic.getTitle(), null, null, null);
        }
    }

    public List<EpicProgress> epicProgress(UUID ownerId, UUID projectId) {
        project(ownerId, projectId); requireEnabled(projectId); initializeItems(ownerId, projectId);
        Map<UUID, Task> taskById = placements.findBoard(ownerId, projectId).stream()
                .collect(Collectors.toMap(com.taskos.project.entity.ProjectTaskPlacement::getTaskId,
                        com.taskos.project.entity.ProjectTaskPlacement::getTask));
        List<EpicProgress> result = new ArrayList<>();
        for (Epic epic : epics.findAllByProjectIdAndArchivedAtIsNullOrderByTitleAsc(projectId)) {
            List<AgileWorkItem> grouped = workItems.findAllByProjectIdAndEpicId(projectId, epic.getId());
            List<AgileWorkItem> eligible = grouped.stream().filter(item -> {
                Task task = taskById.get(item.getTaskId());
                return task != null && task.getArchivedAt() == null && task.getStatus() != TaskStatus.CANCELLED;
            }).toList();
            int completed = 0, completedPoints = 0, estimatedPoints = 0, unestimated = 0;
            for (AgileWorkItem item : eligible) {
                Task task = taskById.get(item.getTaskId());
                if (item.getStoryPoints() == null) unestimated++;
                else {
                    estimatedPoints += item.getStoryPoints();
                    if (task.getStatus() == TaskStatus.COMPLETED) completedPoints += item.getStoryPoints();
                }
                if (task.getStatus() == TaskStatus.COMPLETED) completed++;
            }
            result.add(new EpicProgress(epic, completed, eligible.size(), completedPoints, estimatedPoints, unestimated));
        }
        return result;
    }

    public List<Epic> epics(UUID ownerId, UUID projectId) {
        project(ownerId, projectId); requireEnabled(projectId);
        return epics.findAllByProjectIdAndArchivedAtIsNullOrderByTitleAsc(projectId);
    }

    public Sprint createSprint(UUID ownerId, UUID projectId, String name, String goal, String notes,
            LocalDate start, LocalDate end, Integer capacity) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        validateSprint(name, goal, notes, start, end, capacity);
        Sprint sprint = sprints.save(Sprint.planned(projectId, name, goal, notes, start, end, capacity));
        ceremonies.save(SprintCeremony.create(sprint.getId(), Instant.now()));
        event(ownerId, projectId, sprint.getId(), null, SprintEventType.SPRINT_CREATED, "Sprint planned: " + sprint.getName(), null, null, null);
        return sprint;
    }

    public List<Sprint> sprints(UUID ownerId, UUID projectId) {
        project(ownerId, projectId); requireEnabled(projectId);
        return sprints.findAllByProjectIdOrderByStartDateDesc(projectId);
    }

    public Sprint sprint(UUID ownerId, UUID projectId, UUID sprintId) {
        project(ownerId, projectId); requireEnabled(projectId);
        return sprints.findByIdAndProjectId(sprintId, projectId).orElseThrow(TaskNotFoundException::new);
    }

    public Sprint activeSprint(UUID ownerId, UUID projectId) {
        project(ownerId, projectId); requireEnabled(projectId);
        return sprints.findFirstByProjectIdAndStatus(projectId, SprintStatus.ACTIVE).orElse(null);
    }

    public List<Sprint> completedSprints(UUID ownerId, UUID projectId) {
        project(ownerId, projectId); requireEnabled(projectId);
        return sprints.findAllByProjectIdAndStatusOrderByCompletedAtDesc(projectId, SprintStatus.COMPLETED);
    }

    public boolean hasPlannedOverlap(UUID ownerId, UUID projectId, UUID sprintId) {
        Sprint current = sprint(ownerId, projectId, sprintId);
        return sprints.findAllByProjectIdAndStatusOrderByStartDateAsc(projectId, SprintStatus.PLANNED).stream()
                .filter(other -> !other.getId().equals(sprintId))
                .anyMatch(other -> !other.getEndDate().isBefore(current.getStartDate())
                        && !other.getStartDate().isAfter(current.getEndDate()));
    }

    public List<AgileBacklogItem> planningItems(UUID ownerId, UUID projectId, UUID sprintId) {
        Sprint sprint = sprint(ownerId, projectId, sprintId);
        Set<UUID> assigned = memberships.findAllBySprintIdOrderByPositionAsc(sprintId).stream()
                .filter(item -> CURRENT_MEMBERSHIPS.contains(item.getStatus())).map(SprintMembership::getTaskId).collect(Collectors.toSet());
        return backlogItems(ownerId, projectId).stream().filter(item -> assigned.contains(item.task().getId())).toList();
    }

    public void addToSprint(UUID ownerId, UUID projectId, UUID sprintId, UUID taskId, String reason) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        Sprint sprint = lockedSprint(projectId, sprintId);
        ensureProjectTask(ownerId, projectId, taskId);
        if (sprint.getStatus() != SprintStatus.PLANNED && sprint.getStatus() != SprintStatus.ACTIVE)
            throw new TaskRuleException("Work cannot be added to a completed or cancelled sprint.");
        if (sprint.getStatus() == SprintStatus.ACTIVE) requireReason(reason);
        Task task = placements.findPlacement(taskId, ownerId, projectId).orElseThrow(TaskNotFoundException::new).getTask();
        if (task.getArchivedAt() != null || task.getStatus() == TaskStatus.CANCELLED || task.getStatus() == TaskStatus.COMPLETED)
            throw new TaskRuleException("Restore or reopen this task before adding it to a sprint.");
        if (memberships.findCurrentOtherMembership(taskId, CURRENT_MEMBERSHIPS, CURRENT_SPRINTS, sprintId).isPresent())
            throw new TaskRuleException("This task already belongs to another planned or active sprint.");
        AgileWorkItem item = workItems.findById(taskId).orElseThrow(TaskNotFoundException::new);
        Instant now = Instant.now();
        SprintMembershipStatus memberStatus = sprint.getStatus() == SprintStatus.ACTIVE
                ? SprintMembershipStatus.ACTIVE : SprintMembershipStatus.PLANNED;
        int position = currentMemberships(sprintId).size();
        Optional<SprintMembership> prior = memberships.findBySprintIdAndTaskId(sprintId, taskId);
        if (prior.isPresent()) prior.get().reopen(memberStatus, position, now);
        else {
            SprintMembership membership = SprintMembership.planned(sprintId, taskId, position, now);
            if (memberStatus == SprintMembershipStatus.ACTIVE) membership.activate();
            memberships.save(membership);
        }
        if (sprint.getStatus() == SprintStatus.ACTIVE)
            event(ownerId, projectId, sprintId, taskId, SprintEventType.SCOPE_ADDED, "Work added to the active sprint",
                    null, pointAndStatus(item.getStoryPoints(), task.getStatus()), reason);
    }

    public void removeFromSprint(UUID ownerId, UUID projectId, UUID sprintId, UUID taskId, String reason) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        Sprint sprint = lockedSprint(projectId, sprintId);
        SprintMembership membership = memberships.findBySprintIdAndTaskId(sprintId, taskId).orElseThrow(TaskNotFoundException::new);
        if (sprint.getStatus() != SprintStatus.PLANNED && sprint.getStatus() != SprintStatus.ACTIVE)
            throw new TaskRuleException("Completed sprint scope is read-only.");
        if (!CURRENT_MEMBERSHIPS.contains(membership.getStatus())) throw new TaskRuleException("This task is not in the current sprint scope.");
        if (sprint.getStatus() == SprintStatus.ACTIVE) requireReason(reason);
        Task task = placements.findPlacement(taskId, ownerId, projectId).orElseThrow(TaskNotFoundException::new).getTask();
        AgileWorkItem item = workItems.findById(taskId).orElseThrow(TaskNotFoundException::new);
        membership.close(SprintMembershipStatus.REMOVED, Instant.now());
        if (sprint.getStatus() == SprintStatus.ACTIVE)
            event(ownerId, projectId, sprintId, taskId, SprintEventType.SCOPE_REMOVED, "Work removed from the active sprint",
                    pointAndStatus(item.getStoryPoints(), task.getStatus()), null, reason);
    }

    public void reorderSprintItem(UUID ownerId, UUID projectId, UUID sprintId, UUID taskId, int direction) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        Sprint sprint = lockedSprint(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.PLANNED && sprint.getStatus() != SprintStatus.ACTIVE)
            throw new TaskRuleException("Completed sprint scope is read-only.");
        if (direction != -1 && direction != 1) throw new TaskRuleException("Choose a valid reorder direction.");
        List<SprintMembership> ordered = new ArrayList<>(currentMemberships(sprintId));
        SprintMembership selected = ordered.stream().filter(item -> item.getTaskId().equals(taskId)).findFirst().orElseThrow(TaskNotFoundException::new);
        int to = ordered.indexOf(selected) + direction;
        if (to < 0 || to >= ordered.size()) return;
        Collections.swap(ordered, ordered.indexOf(selected), to);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).moveTo(10000 + i);
        memberships.saveAllAndFlush(ordered);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).moveTo(i);
        memberships.saveAll(ordered);
    }

    public void updateSprint(UUID ownerId, UUID projectId, UUID sprintId, String name, String goal,
            String notes, LocalDate start, LocalDate end, Integer capacity, String reason) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        Sprint sprint = lockedSprint(projectId, sprintId);
        validateSprint(name, goal, notes, start, end, capacity);
        if (sprint.getStatus() != SprintStatus.PLANNED && sprint.getStatus() != SprintStatus.ACTIVE)
            throw new TaskRuleException("Completed sprint history is read-only.");
        if (sprint.getStatus() == SprintStatus.ACTIVE) {
            requireReason(reason);
            if (!sprint.getStartDate().equals(start) || !sprint.getEndDate().equals(end))
                throw new TaskRuleException("Sprint dates cannot change after the sprint starts.");
        }
        String previousGoal = sprint.getGoal();
        try { sprint.update(name, goal, notes, start, end, capacity); }
        catch (IllegalStateException ex) { throw new TaskRuleException(ex.getMessage()); }
        event(ownerId, projectId, sprintId, null, SprintEventType.SPRINT_UPDATED, "Sprint planning updated",
                previousGoal, goal.trim(), reason);
    }

    public void startSprint(UUID ownerId, UUID projectId, UUID sprintId) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        Sprint sprint = lockedSprint(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.PLANNED) throw new TaskRuleException("Only a planned sprint can start.");
        if (sprints.findFirstByProjectIdAndStatus(projectId, SprintStatus.ACTIVE).isPresent())
            throw new TaskRuleException("Finish or cancel the active sprint before starting another.");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        if (sprint.getStartDate().isAfter(today) || sprint.getEndDate().isBefore(today))
            throw new TaskRuleException("Start the sprint within its scheduled UTC date range.");
        List<SprintMembership> selected = memberships.findBySprintIdAndStatus(sprintId, SprintMembershipStatus.PLANNED);
        if (selected.isEmpty()) throw new TaskRuleException("Add at least one work item before starting the sprint.");
        int points = 0;
        List<SprintBaselineItem> snapshot = new ArrayList<>();
        for (SprintMembership membership : selected) {
            var placement = placements.findPlacement(membership.getTaskId(), ownerId, projectId).orElseThrow(TaskNotFoundException::new);
            Task task = placement.getTask();
            if (task.getArchivedAt() != null || task.getStatus() == TaskStatus.COMPLETED || task.getStatus() == TaskStatus.CANCELLED)
                throw new TaskRuleException("Reopen, restore, or remove completed work before starting the sprint.");
            AgileWorkItem item = workItems.findById(task.getId()).orElseThrow(TaskNotFoundException::new);
            if (item.getStoryPoints() != null) points += item.getStoryPoints();
            snapshot.add(SprintBaselineItem.capture(sprintId, task.getId(), task.getTitle(), item.getStoryPoints(), task.getStatus()));
            membership.activate();
        }
        baselines.saveAll(snapshot);
        sprint.start(Instant.now(), points, snapshot.size());
        ceremonies.findById(sprintId).orElseGet(() -> ceremonies.save(SprintCeremony.create(sprintId, Instant.now())));
        event(ownerId, projectId, sprintId, null, SprintEventType.SPRINT_STARTED, "Sprint started with " + snapshot.size() + " work items", null, Integer.toString(points), null);
    }

    public void completeSprint(UUID ownerId, UUID projectId, UUID sprintId, String incompletePolicy, UUID targetSprintId) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        Sprint sprint = lockedSprint(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.ACTIVE) throw new TaskRuleException("Only an active sprint can be completed.");
        Sprint target = null;
        if ("PLANNED_SPRINT".equals(incompletePolicy)) {
            if (targetSprintId == null) throw new TaskRuleException("Choose the planned sprint for unfinished work.");
            target = lockedSprint(projectId, targetSprintId);
            if (target.getStatus() != SprintStatus.PLANNED) throw new TaskRuleException("Choose a planned sprint in this project.");
        } else if (!"BACKLOG".equals(incompletePolicy)) throw new TaskRuleException("Choose where unfinished work should go.");
        List<SprintMembership> selected = memberships.findBySprintIdAndStatus(sprintId, SprintMembershipStatus.ACTIVE);
        Instant now = Instant.now(); int delivered = 0, finalScope = 0, remainingPoints = 0, completedCount = 0, incompleteCount = 0;
        for (SprintMembership membership : selected) {
            Task task = placements.findPlacement(membership.getTaskId(), ownerId, projectId).orElseThrow(TaskNotFoundException::new).getTask();
            AgileWorkItem item = workItems.findById(task.getId()).orElseThrow(TaskNotFoundException::new);
            if (item.getStoryPoints() != null) finalScope += item.getStoryPoints();
            if (task.getStatus() == TaskStatus.COMPLETED) {
                membership.close(SprintMembershipStatus.COMPLETED, now);
                if (item.getStoryPoints() != null) delivered += item.getStoryPoints();
                completedCount++;
            } else {
                membership.close(SprintMembershipStatus.INCOMPLETE, now);
                if (item.getStoryPoints() != null) remainingPoints += item.getStoryPoints();
                incompleteCount++;
                if (target != null) addCarryover(ownerId, projectId, target, task, item, now);
            }
        }
        sprint.complete(now, delivered, finalScope, remainingPoints, completedCount, incompleteCount);
        event(ownerId, projectId, sprintId, null, SprintEventType.SPRINT_COMPLETED,
                "Sprint completed · " + delivered + " delivered story points", null, Integer.toString(delivered), null);
    }

    public void cancelSprint(UUID ownerId, UUID projectId, UUID sprintId, String reason) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        Sprint sprint = lockedSprint(projectId, sprintId);
        requireReason(reason);
        if (sprint.getStatus() != SprintStatus.PLANNED && sprint.getStatus() != SprintStatus.ACTIVE)
            throw new TaskRuleException("A completed or cancelled sprint cannot be cancelled again.");
        SprintMembershipStatus current = sprint.getStatus() == SprintStatus.ACTIVE
                ? SprintMembershipStatus.ACTIVE : SprintMembershipStatus.PLANNED;
        for (SprintMembership membership : memberships.findBySprintIdAndStatus(sprintId, current))
            membership.close(SprintMembershipStatus.CARRIED, Instant.now());
        sprint.cancel(Instant.now());
        event(ownerId, projectId, sprintId, null, SprintEventType.SPRINT_CANCELLED, "Sprint cancelled", null, null, reason.trim());
    }

    public SprintMetrics metrics(UUID ownerId, UUID projectId, UUID sprintId) {
        Sprint sprint = sprint(ownerId, projectId, sprintId);
        List<SprintMembership> sprintItems = memberships.findAllBySprintIdOrderByPositionAsc(sprintId).stream()
                .filter(item -> CURRENT_MEMBERSHIPS.contains(item.getStatus())
                        || (sprint.getStatus() == SprintStatus.COMPLETED
                            && (item.getStatus() == SprintMembershipStatus.COMPLETED || item.getStatus() == SprintMembershipStatus.INCOMPLETE)))
                .toList();
        int total = 0, completed = 0, remaining = 0, completedCount = 0, remainingCount = 0, unestimated = 0;
        for (SprintMembership membership : sprintItems) {
            AgileWorkItem item = workItems.findById(membership.getTaskId()).orElse(null);
            Task task = placements.findPlacement(membership.getTaskId(), ownerId, projectId).map(placement -> placement.getTask()).orElse(null);
            if (item == null || task == null) continue;
            if (item.getStoryPoints() == null) unestimated++;
            else {
                total += item.getStoryPoints();
                if (task.getStatus() == TaskStatus.COMPLETED) completed += item.getStoryPoints();
                else remaining += item.getStoryPoints();
            }
            if (task.getStatus() == TaskStatus.COMPLETED) completedCount++; else remainingCount++;
        }
        if (sprint.getStatus() == SprintStatus.COMPLETED) {
            completed = sprint.getDeliveredPoints(); total = sprint.getFinalScopePoints(); remaining = sprint.getFinalRemainingPoints();
            completedCount = sprint.getCompletedItemCount(); remainingCount = sprint.getIncompleteItemCount();
        }
        int scopes = (int) events.findAllBySprintIdOrderByOccurredAtAsc(sprintId).stream()
                .filter(item -> item.getEventType() == SprintEventType.SCOPE_ADDED
                        || item.getEventType() == SprintEventType.SCOPE_REMOVED
                        || item.getEventType() == SprintEventType.ESTIMATE_CHANGED).count();
        return new SprintMetrics(total, completed, remaining, completedCount, remainingCount, unestimated, scopes);
    }

    public List<BurndownPoint> burndown(UUID ownerId, UUID projectId, UUID sprintId) {
        Sprint sprint = sprint(ownerId, projectId, sprintId);
        List<SprintBaselineItem> baseline = baselines.findAllBySprintId(sprintId);
        int baselinePoints = sprint.getBaselinePoints();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        LocalDate actualEnd = today.isBefore(sprint.getEndDate()) ? today : sprint.getEndDate();
        if (sprint.getCompletedAt() != null) actualEnd = min(actualEnd, sprint.getCompletedAt().atZone(ZoneOffset.UTC).toLocalDate());
        if (sprint.getCancelledAt() != null) actualEnd = min(actualEnd, sprint.getCancelledAt().atZone(ZoneOffset.UTC).toLocalDate());
        Map<UUID, WorkState> state = new HashMap<>();
        baseline.forEach(item -> state.put(item.getTaskId(), new WorkState(item.getStoryPoints(),
                item.getTaskStatus() == TaskStatus.COMPLETED, true)));
        List<SprintEvent> timeline = events.findAllBySprintIdOrderByOccurredAtAsc(sprintId).stream()
                .filter(item -> item.getOccurredAt() != null && (sprint.getStartedAt() == null || !item.getOccurredAt().isBefore(sprint.getStartedAt())))
                .toList();
        List<BurndownPoint> result = new ArrayList<>();
        LocalDate date = sprint.getStartDate();
        while (!date.isAfter(sprint.getEndDate())) {
            int denominator = Math.max(1, (int) ChronoUnit.DAYS.between(sprint.getStartDate(), sprint.getEndDate()));
            int elapsed = (int) ChronoUnit.DAYS.between(sprint.getStartDate(), date);
            int ideal = (int) Math.round(baselinePoints * (1.0 - (double) elapsed / denominator));
            Integer actual = null;
            if (sprint.getStartedAt() != null && !date.isBefore(sprint.getStartedAt().atZone(ZoneOffset.UTC).toLocalDate())
                    && !date.isAfter(actualEnd)) {
                Instant dayEnd = date.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
                for (SprintEvent item : timeline) {
                    if (!item.getOccurredAt().isBefore(dayEnd)) break;
                    applyEvent(state, item);
                }
                actual = state.values().stream().filter(item -> item.active && !item.completed)
                        .mapToInt(item -> item.points == null ? 0 : item.points).sum();
                timeline = timeline.stream().filter(item -> !item.getOccurredAt().isBefore(dayEnd)).toList();
            }
            result.add(new BurndownPoint(date, actual, ideal));
            date = date.plusDays(1);
        }
        return result;
    }

    public List<Sprint> velocity(UUID ownerId, UUID projectId) { return completedSprints(ownerId, projectId); }

    public List<SprintBaselineItem> baselineItems(UUID ownerId, UUID projectId, UUID sprintId) {
        sprint(ownerId, projectId, sprintId); return baselines.findAllBySprintId(sprintId);
    }

    public List<SprintMembership> membershipHistory(UUID ownerId, UUID projectId, UUID sprintId) {
        sprint(ownerId, projectId, sprintId); return memberships.findAllBySprintIdOrderByPositionAsc(sprintId);
    }

    public Double averageVelocity(UUID ownerId, UUID projectId) {
        List<Sprint> recent = completedSprints(ownerId, projectId).stream().limit(5).toList();
        return recent.isEmpty() ? null : recent.stream().mapToInt(Sprint::getDeliveredPoints).average().orElse(0);
    }

    public List<SprintBoardColumn> activeBoard(UUID ownerId, UUID projectId, UUID sprintId) {
        Sprint sprint = sprint(ownerId, projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.ACTIVE) throw new TaskRuleException("This sprint is not active.");
        Set<UUID> assigned = memberships.findBySprintIdAndStatus(sprintId, SprintMembershipStatus.ACTIVE).stream()
                .map(SprintMembership::getTaskId).collect(Collectors.toSet());
        Map<UUID, AgileBacklogItem> cards = backlogItems(ownerId, projectId).stream()
                .filter(item -> assigned.contains(item.task().getId()))
                .collect(Collectors.toMap(item -> item.task().getId(), item -> item));
        List<com.taskos.project.entity.ProjectTaskPlacement> board = placements.findBoard(ownerId, projectId);
        List<SprintBoardColumn> result = new ArrayList<>();
        for (BoardColumn column : columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId)) {
            List<AgileBacklogItem> group = board.stream()
                    .filter(placement -> placement.getColumnId().equals(column.getId()) && assigned.contains(placement.getTaskId()))
                    .map(placement -> cards.get(placement.getTaskId())).filter(Objects::nonNull).toList();
            result.add(new SprintBoardColumn(column, group));
        }
        return result;
    }

    public void moveActiveTask(UUID ownerId, UUID projectId, UUID sprintId, UUID taskId, UUID columnId, int position) {
        moveActiveTask(ownerId, ownerId, projectId, sprintId, taskId, columnId, position);
    }

    public void moveActiveTask(UUID actorId, UUID ownerId, UUID projectId, UUID sprintId,
            UUID taskId, UUID columnId, int position) {
        lockProject(ownerId, projectId); writable(project(ownerId, projectId)); requireEnabled(projectId);
        Sprint sprint = lockedSprint(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.ACTIVE || memberships.findBySprintIdAndTaskId(sprintId, taskId)
                .filter(item -> item.getStatus() == SprintMembershipStatus.ACTIVE).isEmpty())
            throw new TaskRuleException("Only tasks in the active sprint can move on this board.");
        if (project(ownerId, projectId).getWorkspaceId() == null)
            projectService.moveTask(ownerId, projectId, taskId, columnId, position);
        else projectService.moveTaskInSharedProject(actorId, projectId, taskId, columnId, position);
    }

    public List<SprintEvent> recentActivity(UUID ownerId, UUID projectId) {
        project(ownerId, projectId); requireEnabled(projectId);
        return events.findByProjectIdOrderByOccurredAtDesc(projectId, PageRequest.of(0, 30));
    }

    public List<SprintEvent> sprintActivity(UUID ownerId, UUID projectId, UUID sprintId) {
        sprint(ownerId, projectId, sprintId);
        return events.findAllBySprintIdOrderByOccurredAtAsc(sprintId);
    }

    public SprintCeremony ceremony(UUID ownerId, UUID projectId, UUID sprintId) {
        sprint(ownerId, projectId, sprintId);
        return ceremonies.findById(sprintId).orElseGet(() -> ceremonies.save(SprintCeremony.create(sprintId, Instant.now())));
    }

    public void saveReview(UUID ownerId, UUID projectId, UUID sprintId, String outcome, String delivered,
            String incomplete, String demo, String feedback, String accepted, String needsRework, String followUps) {
        lockProject(ownerId, projectId); writable(project(ownerId, projectId));
        Sprint sprint = lockedSprint(projectId, sprintId);
        if (sprint.getStatus() == SprintStatus.PLANNED || sprint.getStatus() == SprintStatus.CANCELLED)
            throw new TaskRuleException("Add sprint review notes while the sprint is active or after it completes.");
        validateCeremony(outcome, delivered, incomplete, demo, feedback, accepted, needsRework, followUps);
        SprintCeremony ceremony = ceremonies.findById(sprintId).orElseGet(() -> SprintCeremony.create(sprintId, Instant.now()));
        ceremony.saveReview(outcome, delivered, incomplete, demo, feedback, accepted, needsRework, followUps, Instant.now());
        ceremonies.save(ceremony);
    }

    public void saveRetrospective(UUID ownerId, UUID projectId, UUID sprintId, String wentWell,
            String improve, String tryNext, String actions) {
        lockProject(ownerId, projectId); writable(project(ownerId, projectId));
        Sprint sprint = lockedSprint(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.COMPLETED)
            throw new TaskRuleException("Complete the sprint before recording retrospective notes.");
        validateCeremony(wentWell, improve, tryNext, actions);
        SprintCeremony ceremony = ceremonies.findById(sprintId).orElseGet(() -> SprintCeremony.create(sprintId, Instant.now()));
        ceremony.saveRetrospective(wentWell, improve, tryNext, actions, Instant.now()); ceremonies.save(ceremony);
    }

    public List<SprintActionItem> retrospectiveActions(UUID ownerId, UUID projectId, UUID sprintId) {
        sprint(ownerId, projectId, sprintId);
        return actionItems.findAllBySprintIdOrderByPositionAsc(sprintId);
    }

    public SprintActionItem addRetrospectiveAction(UUID ownerId, UUID projectId, UUID sprintId, String description) {
        lockProject(ownerId, projectId); writable(project(ownerId, projectId)); requireEnabled(projectId);
        Sprint sprint = lockedSprint(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.COMPLETED)
            throw new TaskRuleException("Add retrospective action items after the sprint completes.");
        if (description == null || description.isBlank() || description.trim().length() > 1000)
            throw new TaskRuleException("Enter an action item of 1 to 1,000 characters.");
        if (actionItems.countBySprintId(sprintId) >= 50)
            throw new TaskRuleException("A retrospective can have up to 50 action items.");
        return actionItems.save(SprintActionItem.create(sprintId, description, (int) actionItems.countBySprintId(sprintId), Instant.now()));
    }

    public UUID convertRetrospectiveAction(UUID ownerId, UUID projectId, UUID sprintId, UUID actionId) {
        return convertRetrospectiveAction(ownerId, ownerId, projectId, sprintId, actionId);
    }

    public UUID convertRetrospectiveAction(UUID actorId, UUID ownerId, UUID projectId, UUID sprintId, UUID actionId) {
        Project project = lockProject(ownerId, projectId); writable(project); requireEnabled(projectId);
        Sprint sprint = lockedSprint(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.COMPLETED)
            throw new TaskRuleException("Convert retrospective action items after the sprint completes.");
        SprintActionItem item = actionItems.findByIdAndSprintId(actionId, sprintId).orElseThrow(TaskNotFoundException::new);
        if (item.getTaskId() != null) return item.getTaskId();
        BoardColumn todo = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId).stream()
                .filter(column -> column.getStage() == BoardStage.TODO).findFirst().orElseThrow(TaskNotFoundException::new);
        String title = item.getDescription().length() <= 160 ? item.getDescription() : item.getDescription().substring(0, 160);
        String description = "Retrospective action from " + sprint.getName() + ":\n\n" + item.getDescription();
        UUID taskId = project.getWorkspaceId() == null
                ? projectService.createTask(ownerId, projectId, todo.getId(), title, description, TaskPriority.MEDIUM, null)
                : projectService.createSharedProjectTask(actorId, projectId, todo.getId(), title, description, TaskPriority.MEDIUM, null);
        item.linkTask(taskId);
        return taskId;
    }

    public List<SprintDailyNote> dailyNotes(UUID ownerId, UUID projectId, UUID sprintId) {
        sprint(ownerId, projectId, sprintId); return dailyNotes.findAllBySprintIdOrderByCreatedAtDesc(sprintId);
    }

    public void addDailyNote(UUID ownerId, UUID projectId, UUID sprintId, String progress, String next,
            String blockers, String notes) {
        Project project = lockProject(ownerId, projectId); writable(project);
        Sprint sprint = lockedSprint(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.ACTIVE) throw new TaskRuleException("Daily Scrum notes are available during an active sprint.");
        if (tooLong(progress, 2000) || tooLong(next, 2000) || tooLong(blockers, 2000) || tooLong(notes, 3000))
            throw new TaskRuleException("Daily Scrum note fields exceed their allowed length.");
        if (allBlank(progress, next, blockers, notes)) throw new TaskRuleException("Enter at least one daily Scrum note.");
        dailyNotes.save(SprintDailyNote.create(sprintId, ownerId, progress, next, blockers, notes, Instant.now()));
        event(ownerId, projectId, sprintId, null, SprintEventType.DAILY_NOTE_ADDED, "Daily Scrum note added", null, null, null);
    }

    public List<SprintImpediment> impediments(UUID ownerId, UUID projectId, UUID sprintId) {
        sprint(ownerId, projectId, sprintId); return impediments.findAllBySprintIdOrderByReportedAtDesc(sprintId);
    }

    public void addImpediment(UUID ownerId, UUID projectId, UUID sprintId, UUID taskId, String description, String notes) {
        Project project = lockProject(ownerId, projectId); writable(project);
        Sprint sprint = lockedSprint(projectId, sprintId);
        if (sprint.getStatus() != SprintStatus.ACTIVE) throw new TaskRuleException("Impediments can be added during an active sprint.");
        if (description == null || description.isBlank() || description.trim().length() > 1000 || tooLong(notes, 2000))
            throw new TaskRuleException("Enter a blocker description of up to 1,000 characters and notes of up to 2,000 characters.");
        if (taskId != null && memberships.findBySprintIdAndTaskId(sprintId, taskId)
                .filter(item -> item.getStatus() == SprintMembershipStatus.ACTIVE).isEmpty())
            throw new TaskRuleException("Link the blocker to a task in this active sprint.");
        impediments.save(SprintImpediment.create(sprintId, taskId, ownerId, description, notes, Instant.now()));
        event(ownerId, projectId, sprintId, taskId, SprintEventType.IMPEDIMENT_REPORTED, "Sprint impediment reported", null, null, null);
    }

    public void resolveImpediment(UUID ownerId, UUID projectId, UUID sprintId, UUID impedimentId) {
        lockProject(ownerId, projectId); writable(project(ownerId, projectId));
        SprintImpediment item = impediments.findByIdAndSprintId(impedimentId, sprintId).orElseThrow(TaskNotFoundException::new);
        if (item.getStatus() == ImpedimentStatus.OPEN) {
            item.resolve(Instant.now()); event(ownerId, projectId, sprintId, item.getTaskId(),
                    SprintEventType.IMPEDIMENT_RESOLVED, "Sprint impediment resolved", null, null, null);
        }
    }

    @EventListener
    public void recordTaskStatus(TaskStatusChangedEvent change) {
        Optional<SprintMembership> current = memberships.findCurrentOtherMembership(change.taskId(),
                List.of(SprintMembershipStatus.ACTIVE), List.of(SprintStatus.ACTIVE), null);
        if (current.isEmpty()) return;
        SprintMembership membership = current.get();
        Sprint sprint = sprints.findById(membership.getSprintId()).orElse(null);
        AgileWorkItem item = workItems.findById(change.taskId()).orElse(null);
        if (sprint == null || item == null) return;
        if (change.status() == TaskStatus.COMPLETED) {
            event(change.ownerId(), sprint.getProjectId(), sprint.getId(), change.taskId(), SprintEventType.TASK_COMPLETED,
                    "Sprint task completed", change.previousStatus().name(), pointAndStatus(item.getStoryPoints(), change.status()), null);
        } else if (change.previousStatus() == TaskStatus.COMPLETED) {
            event(change.ownerId(), sprint.getProjectId(), sprint.getId(), change.taskId(), SprintEventType.TASK_REOPENED,
                    "Sprint task reopened", change.previousStatus().name(), pointAndStatus(item.getStoryPoints(), change.status()), null);
        }
    }

    private void addCarryover(UUID ownerId, UUID projectId, Sprint target, Task task, AgileWorkItem item, Instant now) {
        Optional<SprintMembership> previous = memberships.findBySprintIdAndTaskId(target.getId(), task.getId());
        int position = currentMemberships(target.getId()).size();
        if (previous.isPresent()) {
            if (CURRENT_MEMBERSHIPS.contains(previous.get().getStatus()))
                throw new TaskRuleException("An unfinished task is already in the selected planned sprint.");
            previous.get().reopen(SprintMembershipStatus.PLANNED, position, now);
        } else memberships.save(SprintMembership.planned(target.getId(), task.getId(), position, now));
        event(ownerId, projectId, target.getId(), task.getId(), SprintEventType.SCOPE_ADDED,
                "Unfinished work carried into " + target.getName(), null,
                pointAndStatus(item.getStoryPoints(), task.getStatus()), "Carried from a completed sprint");
    }

    private List<SprintMembership> currentMemberships(UUID sprintId) {
        return memberships.findAllBySprintIdOrderByPositionAsc(sprintId).stream()
                .filter(item -> CURRENT_MEMBERSHIPS.contains(item.getStatus())).toList();
    }

    private void normalizeBacklogOrder(List<AgileWorkItem> ordered) {
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).moveTo(10000 + i);
        workItems.saveAllAndFlush(ordered);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).moveTo(i);
        workItems.saveAll(ordered);
    }

    private void normalizeCriteria(UUID taskId) {
        List<AcceptanceCriterion> ordered = new ArrayList<>(criteria.findAllByTaskIdOrderByPositionAsc(taskId));
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).moveTo(1000 + i);
        criteria.saveAllAndFlush(ordered);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).moveTo(i);
        criteria.saveAll(ordered);
    }

    private void initializeItems(UUID ownerId, UUID projectId) {
        Project project = lockProject(ownerId, projectId);
        if (settings.findById(projectId).filter(AgileProjectSettings::isEnabled).isEmpty()) return;
        List<com.taskos.project.entity.ProjectTaskPlacement> board = placements.findBoard(ownerId, projectId);
        Set<UUID> known = workItems.findAllByProjectIdOrderByBacklogPositionAsc(projectId).stream()
                .map(AgileWorkItem::getTaskId).collect(Collectors.toSet());
        int nextPosition = workItems.findAllByProjectIdOrderByBacklogPositionAsc(projectId).stream()
                .mapToInt(AgileWorkItem::getBacklogPosition).max().orElse(-1) + 1;
        List<AgileWorkItem> missing = new ArrayList<>();
        for (var placement : board) if (!known.contains(placement.getTaskId()))
            missing.add(AgileWorkItem.create(placement.getTaskId(), project.getId(), nextPosition++));
        if (!missing.isEmpty()) workItems.saveAll(missing);
        List<String> defaults = Arrays.stream(settings.findById(projectId).orElseThrow().getDefinitionOfDone().split("\\R"))
                .map(String::trim).filter(line -> !line.isEmpty()).limit(MAX_CRITERIA).toList();
        if (!defaults.isEmpty()) {
            List<UUID> taskIds = workItems.findAllByProjectIdOrderByBacklogPositionAsc(projectId).stream()
                    .map(AgileWorkItem::getTaskId).toList();
            Set<UUID> initialized = definitionOfDoneCriteria.findAllByTaskIdInOrderByPositionAsc(taskIds).stream()
                    .map(DefinitionOfDoneCriterion::getTaskId).collect(Collectors.toSet());
            List<DefinitionOfDoneCriterion> seeded = new ArrayList<>();
            for (UUID taskId : taskIds) if (!initialized.contains(taskId))
                for (int i = 0; i < defaults.size(); i++)
                    seeded.add(DefinitionOfDoneCriterion.create(taskId, defaults.get(i), i));
            if (!seeded.isEmpty()) definitionOfDoneCriteria.saveAll(seeded);
        }
    }

    private void ensureProjectTask(UUID ownerId, UUID projectId, UUID taskId) {
        placements.findPlacement(taskId, ownerId, projectId).orElseThrow(TaskNotFoundException::new);
        if (workItems.findById(taskId).filter(item -> item.getProjectId().equals(projectId)).isEmpty())
            throw new TaskNotFoundException();
    }

    private Project project(UUID ownerId, UUID projectId) {
        return projects.findByIdAndOwner_IdIncludingShared(projectId, ownerId).orElseThrow(TaskNotFoundException::new);
    }

    private Project lockProject(UUID ownerId, UUID projectId) {
        return projects.findLockedByIdAndOwnerIncludingShared(projectId, ownerId).orElseThrow(TaskNotFoundException::new);
    }

    private void writable(Project project) {
        if (project.getArchivedAt() != null) throw new TaskRuleException("Restore this project before changing its Scrum workspace.");
    }

    private AgileProjectSettings requireEnabled(UUID projectId) {
        return settings.findById(projectId).filter(AgileProjectSettings::isEnabled)
                .orElseThrow(() -> new TaskRuleException("Enable Scrum in the project Agile workspace first."));
    }

    private Sprint lockedSprint(UUID projectId, UUID sprintId) {
        return sprints.findLockedByIdAndProjectId(sprintId, projectId).orElseThrow(TaskNotFoundException::new);
    }

    private void validatePoints(Integer points) {
        if (points != null && Arrays.stream(AgileWorkItem.STORY_POINT_SCALE).noneMatch(value -> value == points))
            throw new TaskRuleException("Use a story point estimate from 0, 1, 2, 3, 5, 8, 13, or 20; leave blank for unestimated.");
    }

    private void validateEpic(String title, String description) {
        if (title == null || title.isBlank() || title.trim().length() > 160)
            throw new TaskRuleException("Enter an epic title of 1 to 160 characters.");
        if (tooLong(description, 2000)) throw new TaskRuleException("Epic descriptions must be 2,000 characters or fewer.");
    }

    private void validateSprint(String name, String goal, String notes, LocalDate start, LocalDate end, Integer capacity) {
        if (name == null || name.isBlank() || name.trim().length() > 100) throw new TaskRuleException("Enter a sprint name of 1 to 100 characters.");
        if (goal == null || goal.isBlank() || goal.trim().length() > 500) throw new TaskRuleException("Enter a sprint goal of 1 to 500 characters.");
        if (tooLong(notes, 3000)) throw new TaskRuleException("Planning notes must be 3,000 characters or fewer.");
        if (start == null || end == null || end.isBefore(start)) throw new TaskRuleException("Choose a valid sprint date range.");
        if (ChronoUnit.DAYS.between(start, end) > MAX_SPRINT_DAYS) throw new TaskRuleException("A sprint can span at most 90 days.");
        if (capacity != null && capacity < 0) throw new TaskRuleException("Planning capacity cannot be negative.");
    }

    private void validateCeremony(String... fields) {
        if (Arrays.stream(fields).anyMatch(value -> tooLong(value, 5000)))
            throw new TaskRuleException("Ceremony notes must be 5,000 characters or fewer per field.");
    }

    private void requireReason(String reason) {
        if (reason == null || reason.isBlank() || reason.trim().length() > 500)
            throw new TaskRuleException("Enter a reason of 1 to 500 characters for this sprint change.");
    }

    private boolean tooLong(String value, int max) { return value != null && value.length() > max; }
    private boolean allBlank(String... values) { return Arrays.stream(values).allMatch(value -> value == null || value.isBlank()); }
    private LocalDate min(LocalDate left, LocalDate right) { return left.isBefore(right) ? left : right; }
    private String pointValue(Integer points) { return points == null ? "?" : Integer.toString(points); }
    private String pointAndStatus(Integer points, TaskStatus status) { return pointValue(points) + "|" + status.name(); }

    private void event(UUID actorId, UUID projectId, UUID sprintId, UUID taskId, SprintEventType type,
            String summary, String previous, String next, String reason) {
        events.save(SprintEvent.create(projectId, sprintId, taskId, actorId, type, limit(summary, 240),
                limit(previous, 500), limit(next, 500), limit(reason, 500), Instant.now()));
    }

    private String limit(String value, int max) { return value == null || value.length() <= max ? value : value.substring(0, max); }

    private void applyEvent(Map<UUID, WorkState> state, SprintEvent event) {
        if (event.getTaskId() == null) return;
        WorkState current = state.get(event.getTaskId());
        switch (event.getEventType()) {
            case SCOPE_ADDED -> state.put(event.getTaskId(), parseState(event.getNewValue(), true));
            case SCOPE_REMOVED -> { if (current != null) current.active = false; }
            case ESTIMATE_CHANGED -> { if (current != null) current.points = parsePoints(event.getNewValue()); }
            case TASK_COMPLETED -> {
                if (current != null) { current.completed = true; current.points = parsePoints(event.getNewValue()); }
            }
            case TASK_REOPENED -> {
                if (current != null) { current.completed = false; current.points = parsePoints(event.getNewValue()); }
            }
            default -> { }
        }
    }

    private WorkState parseState(String value, boolean active) {
        String[] parts = value == null ? new String[0] : value.split("\\|", 2);
        return new WorkState(parts.length == 0 ? null : parsePoints(parts[0]),
                parts.length > 1 && TaskStatus.COMPLETED.name().equals(parts[1]), active);
    }

    private Integer parsePoints(String value) {
        if (value == null || value.isBlank() || value.equals("?")) return null;
        String numeric = value.contains("|") ? value.substring(0, value.indexOf('|')) : value;
        try { return Integer.valueOf(numeric); } catch (NumberFormatException ignored) { return null; }
    }

    private static final class WorkState {
        private Integer points;
        private boolean completed;
        private boolean active;
        private WorkState(Integer points, boolean completed, boolean active) {
            this.points = points; this.completed = completed; this.active = active;
        }
    }
}
