package com.taskos.project.service;

import com.taskos.agile.repository.AgileProjectSettingsRepository;
import com.taskos.agile.repository.AgileWorkItemRepository;
import com.taskos.collaboration.service.ProjectAccessService;
import com.taskos.collaboration.service.WorkspacePermission;
import com.taskos.collaboration.service.WorkspaceTaskService;
import com.taskos.project.dto.MilestoneProgress;
import com.taskos.project.dto.ProjectBoardColumnView;
import com.taskos.project.dto.ProjectForm;
import com.taskos.project.dto.ProjectProgress;
import com.taskos.project.dto.ProjectTemplateDefinition;
import com.taskos.project.entity.*;
import com.taskos.project.repository.*;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.event.TaskStatusChangedEvent;
import com.taskos.task.event.TaskActivityRecordedEvent;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskNotFoundException;
import com.taskos.task.service.TaskRuleException;
import com.taskos.task.service.TaskService;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@Service
@Transactional
public class ProjectService {
    private static final int PAGE_SIZE = 20;
    private static final int MAX_BOARD_TASKS = 250;
    private static final int MAX_COLUMNS = 12;
    private static final int MAX_MILESTONES = 50;
    private static final int MAX_TEMPLATE_TASKS = 100;
    private static final Set<TaskStatus> WIP_EXCLUDED = Set.of(TaskStatus.COMPLETED, TaskStatus.CANCELLED);
    private static final List<BoardStage> DEFAULT_STAGES = List.of(BoardStage.BACKLOG, BoardStage.TODO,
            BoardStage.IN_PROGRESS, BoardStage.IN_REVIEW, BoardStage.ON_HOLD, BoardStage.DONE, BoardStage.CANCELLED);

    private final ProjectRepository projects;
    private final BoardColumnRepository columns;
    private final ProjectTaskPlacementRepository placements;
    private final ProjectMilestoneRepository milestones;
    private final ProjectActivityRepository activity;
    private final ProjectTemplateRepository templates;
    private final UserAccountRepository users;
    private final TaskRepository tasks;
    private final TaskService taskService;
    private final JsonMapper objectMapper;
    private final AgileProjectSettingsRepository agileSettings;
    private final AgileWorkItemRepository agileItems;
    private final ProjectAccessService projectAccess;
    private final WorkspaceTaskService workspaceTasks;

    public ProjectService(ProjectRepository projects, BoardColumnRepository columns,
            ProjectTaskPlacementRepository placements, ProjectMilestoneRepository milestones,
            ProjectActivityRepository activity, ProjectTemplateRepository templates,
            UserAccountRepository users, TaskRepository tasks, TaskService taskService,
            JsonMapper objectMapper, AgileProjectSettingsRepository agileSettings, AgileWorkItemRepository agileItems,
            ProjectAccessService projectAccess, WorkspaceTaskService workspaceTasks) {
        this.projects = projects; this.columns = columns; this.placements = placements;
        this.milestones = milestones; this.activity = activity; this.templates = templates;
        this.users = users; this.tasks = tasks; this.taskService = taskService;
        this.objectMapper = objectMapper; this.agileSettings = agileSettings; this.agileItems = agileItems;
        this.projectAccess = projectAccess; this.workspaceTasks = workspaceTasks;
    }

    @Transactional(readOnly = true)
    public Page<Project> search(UUID ownerId, String search, ProjectStatus status, Boolean archived, int page) {
        int safePage = Math.max(0, Math.min(page, 10000));
        String query = search == null || search.isBlank() ? null : search.trim().toLowerCase(Locale.ROOT);
        if (query != null && query.length() > 100) query = query.substring(0, 100);
        return projects.search(ownerId, status, archived, query,
                PageRequest.of(safePage, PAGE_SIZE, Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"))));
    }

    @Transactional(readOnly = true)
    public List<ProjectTemplate> templates(UUID ownerId) {
        return templates.findAllByOwner_IdOrderByNameAsc(ownerId);
    }

    @Transactional(readOnly = true)
    public ProjectTemplateDefinition templateDefinition(UUID ownerId, UUID templateId) {
        return definition(template(ownerId, templateId));
    }

    public UUID create(UUID ownerId, ProjectForm form, UUID templateId) {
        validateProjectInput(form);
        UserAccount owner = user(ownerId);
        ProjectTemplate template = templateId == null ? null : template(ownerId, templateId);
        ProjectTemplateDefinition definition = template == null ? null : definition(template);
        List<ProjectTemplateDefinition.Column> initialColumns = definition == null
                ? defaultColumns() : definition.columns();
        validateColumns(initialColumns);

        Instant now = Instant.now();
        Project project = projects.saveAndFlush(Project.create(owner, form.getName(), form.getDescription(),
                form.getStatus(), form.getStartDate(), form.getTargetEndDate(), form.getColor(), now));
        List<BoardColumn> savedColumns = new ArrayList<>();
        for (ProjectTemplateDefinition.Column column : initialColumns) {
            savedColumns.add(columns.save(BoardColumn.create(ownerId, project.getId(), column.name(),
                    column.stage(), column.position(), column.wipLimit(), column.wipPolicy())));
        }
        columns.flush();

        Map<Integer, ProjectMilestone> savedMilestones = new HashMap<>();
        if (definition != null) {
            for (ProjectTemplateDefinition.Milestone milestone : definition.milestones()) {
                LocalDate target = targetDate(form.getStartDate(), milestone.dueInDays());
                ProjectMilestone saved = milestones.save(ProjectMilestone.create(project.getId(), milestone.title(),
                        milestone.description(), target, milestone.position(), now));
                savedMilestones.put(milestone.position(), saved);
            }
            milestones.flush();
            Map<Integer, BoardColumn> byPosition = new HashMap<>();
            savedColumns.forEach(column -> byPosition.put(column.getPosition(), column));
            Map<UUID, Integer> ranks = new HashMap<>();
            for (ProjectTemplateDefinition.TaskBlueprint task : definition.tasks()) {
                BoardColumn column = byPosition.get(task.columnPosition());
                if (column == null) throw new TaskRuleException("A template task refers to a missing workflow column.");
                TaskForm taskForm = new TaskForm();
                taskForm.setTitle(task.title()); taskForm.setDescription(task.description());
                taskForm.setStatus(column.getStage().taskStatus());
                taskForm.setPriority(task.priority() == null ? TaskPriority.MEDIUM : task.priority());
                taskForm.setDueDate(targetDate(form.getStartDate(), task.dueInDays()));
                UUID taskId = taskService.create(ownerId, taskForm);
                Task created = tasks.findDetailByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
                ProjectTaskPlacement placement = ProjectTaskPlacement.create(ownerId, project.getId(), created, column,
                        ranks.merge(column.getId(), 1, Integer::sum) - 1);
                if (task.milestonePosition() != null) {
                    ProjectMilestone milestone = savedMilestones.get(task.milestonePosition());
                    if (milestone == null) throw new TaskRuleException("A template task refers to a missing milestone.");
                    placement.setMilestone(milestone);
                }
                placements.save(placement);
            }
        }
        record(ownerId, project.getId(), null, ProjectActivityType.PROJECT_CREATED, "Project created");
        if (template != null) record(ownerId, project.getId(), null, ProjectActivityType.TEMPLATE_APPLIED,
                "Project created from template “" + template.getName() + "”");
        return project.getId();
    }

    public void update(UUID ownerId, UUID projectId, ProjectForm form) {
        validateProjectInput(form);
        Project project = lockProject(ownerId, projectId);
        if (project.getArchivedAt() != null) throw new TaskRuleException("Restore this project before editing it.");
        if (project.getVersion() != form.getVersion()) throw new TaskRuleException("This project changed in another session. Reload it and try again.");
        ProjectStatus before = project.getStatus();
        try {
            project.update(form.getName(), form.getDescription(), form.getStatus(), form.getStartDate(),
                    form.getTargetEndDate(), form.getColor(), Instant.now());
        } catch (IllegalArgumentException ex) {
            throw new TaskRuleException(ex.getMessage());
        }
        if (before != form.getStatus()) record(ownerId, projectId, null, ProjectActivityType.PROJECT_STATUS_CHANGED,
                "Project status changed to " + form.getStatus().name());
        record(ownerId, projectId, null, ProjectActivityType.PROJECT_UPDATED, "Project details updated");
    }

    public void archive(UUID ownerId, UUID projectId) {
        Project project = lockProject(ownerId, projectId);
        if (project.archive(Instant.now())) record(ownerId, projectId, null, ProjectActivityType.PROJECT_ARCHIVED, "Project archived");
    }

    public void restore(UUID ownerId, UUID projectId) {
        Project project = lockProject(ownerId, projectId);
        if (project.restore(Instant.now())) record(ownerId, projectId, null, ProjectActivityType.PROJECT_RESTORED, "Project restored");
    }

    public void delete(UUID ownerId, UUID projectId, boolean confirmed) {
        if (!confirmed) throw new TaskRuleException("Confirm project deletion to continue.");
        Project project = lockProject(ownerId, projectId);
        if (agileSettings.existsById(projectId))
            throw new TaskRuleException("This project has Scrum history. Archive it to preserve its sprint records.");
        placements.deleteAll(placements.findBoard(ownerId, projectId));
        placements.flush();
        projects.delete(project);
        projects.flush();
    }

    public void addExistingTask(UUID ownerId, UUID projectId, UUID taskId) {
        Task task = tasks.findLockedByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
        Project project = lockProject(ownerId, projectId);
        ensureWritable(project);
        validateAttachableTask(task);
        if (placements.findByTaskIdAndOwnerId(taskId, ownerId).isPresent())
            throw new TaskRuleException("This task already belongs to a project. Move or remove it first.");
        BoardColumn column = columnForStatus(ownerId, projectId, task.getStatus());
        enforceWip(ownerId, projectId, column, task.getStatus(), taskId, false);
        int position = (int) placements.countByOwnerIdAndProjectIdAndColumnId(ownerId, projectId, column.getId());
        placements.save(ProjectTaskPlacement.create(ownerId, projectId, task, column, position));
        record(ownerId, projectId, taskId, ProjectActivityType.TASK_ADDED, "Task added to “" + project.getName() + "”");
    }

    public UUID createTask(UUID ownerId, UUID projectId, UUID columnId, String title,
            String description, TaskPriority priority, LocalDate dueDate) {
        Project project = lockProject(ownerId, projectId);
        ensureWritable(project);
        BoardColumn column = column(ownerId, projectId, columnId);
        validateTaskInput(title, description);
        if (dueDate != null && project.getStartDate() != null && dueDate.isBefore(project.getStartDate()))
            throw new TaskRuleException("The task due date must be on or after the project start date.");
        enforceWip(ownerId, projectId, column, column.getStage().taskStatus(), null, false);
        TaskForm form = new TaskForm();
        form.setTitle(title.trim()); form.setDescription(description); form.setStatus(column.getStage().taskStatus());
        form.setPriority(priority == null ? TaskPriority.MEDIUM : priority); form.setDueDate(dueDate);
        UUID taskId = taskService.create(ownerId, form);
        Task task = tasks.findDetailByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
        int position = (int) placements.countByOwnerIdAndProjectIdAndColumnId(ownerId, projectId, columnId);
        placements.save(ProjectTaskPlacement.create(ownerId, projectId, task, column, position));
        record(ownerId, projectId, taskId, ProjectActivityType.TASK_ADDED, "Task created in “" + project.getName() + "”");
        return taskId;
    }

    public UUID createSharedProjectTask(UUID actorId, UUID projectId, UUID columnId, String title,
            String description, TaskPriority priority, LocalDate dueDate) {
        Project visible = projectAccess.require(actorId, projectId, WorkspacePermission.EDIT_TASK);
        UUID workspaceId = visible.getWorkspaceId();
        if (workspaceId == null) return createTask(actorId, projectId, columnId, title, description, priority, dueDate);
        UUID ownerId = visible.getOwner().getId();
        Project project = projects.findLockedByIdAndOwnerIncludingShared(projectId, ownerId)
                .filter(row -> workspaceId.equals(row.getWorkspaceId())).orElseThrow(TaskNotFoundException::new);
        ensureWritable(project);
        BoardColumn column = column(ownerId, projectId, columnId);
        validateTaskInput(title, description);
        if (column.getStage().taskStatus() != TaskStatus.TODO)
            throw new TaskRuleException("New shared project tasks must start in a To Do column.");
        if (dueDate != null && project.getStartDate() != null && dueDate.isBefore(project.getStartDate()))
            throw new TaskRuleException("The task due date must be on or after the project start date.");
        enforceWip(ownerId, projectId, column, TaskStatus.TODO, null, false);
        UUID taskId = workspaceTasks.createForProject(actorId, ownerId, workspaceId, title, description, priority, dueDate);
        Task task = tasks.findByIdAndWorkspace(taskId, workspaceId).orElseThrow(TaskNotFoundException::new);
        int position = (int) placements.countByOwnerIdAndProjectIdAndColumnId(ownerId, projectId, columnId);
        placements.save(ProjectTaskPlacement.create(ownerId, projectId, task, column, position));
        record(ownerId, projectId, taskId, ProjectActivityType.TASK_ADDED, "Task created in “" + project.getName() + "”");
        return taskId;
    }

    public void removeTask(UUID ownerId, UUID projectId, UUID taskId) {
        tasks.findLockedByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
        Project project = lockProject(ownerId, projectId);
        ensureWritable(project);
        if (agileItems.existsByTaskId(taskId))
            throw new TaskRuleException("This task has Scrum history. Keep it in the project and archive it if needed.");
        ProjectTaskPlacement placement = placements.findPlacement(taskId, ownerId, projectId).orElseThrow(TaskNotFoundException::new);
        List<ProjectTaskPlacement> sameColumn = placements.findBoard(ownerId, projectId).stream()
                .filter(item -> item.getColumnId().equals(placement.getColumnId()) && !item.getTaskId().equals(taskId)).toList();
        placements.delete(placement); placements.flush(); compactPositions(sameColumn);
        record(ownerId, projectId, taskId, ProjectActivityType.TASK_REMOVED, "Task removed from “" + project.getName() + "”");
    }

    public void moveTask(UUID ownerId, UUID projectId, UUID taskId, UUID targetColumnId, int requestedPosition) {
        Task task = tasks.findLockedByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
        Project project = lockProject(ownerId, projectId);
        moveTask(ownerId, project, task, taskId, targetColumnId, requestedPosition,
                () -> taskService.transition(ownerId, taskId, column(ownerId, projectId, targetColumnId).getStage().taskStatus()));
    }

    public void moveTaskInSharedProject(UUID actorId, UUID projectId, UUID taskId,
            UUID targetColumnId, int requestedPosition) {
        Project visible = projectAccess.require(actorId, projectId, WorkspacePermission.EDIT_TASK);
        UUID workspaceId = visible.getWorkspaceId();
        if (workspaceId == null) throw new TaskNotFoundException();
        UUID ownerId = visible.getOwner().getId();
        Project project = projects.findLockedByIdAndOwnerIncludingShared(projectId, ownerId)
                .filter(row -> workspaceId.equals(row.getWorkspaceId())).orElseThrow(TaskNotFoundException::new);
        Task task = tasks.findLockedByIdAndWorkspace(taskId, workspaceId).orElseThrow(TaskNotFoundException::new);
        moveTask(ownerId, project, task, taskId, targetColumnId, requestedPosition,
                () -> workspaceTasks.changeStatus(actorId, workspaceId, taskId,
                        column(ownerId, projectId, targetColumnId).getStage().taskStatus()));
    }

    private void moveTask(UUID ownerId, Project project, Task task, UUID taskId,
            UUID targetColumnId, int requestedPosition, Runnable statusTransition) {
        UUID projectId = project.getId();
        ensureWritable(project); validateAttachableTask(task);
        ProjectTaskPlacement current = placements.findPlacement(taskId, ownerId, projectId).orElseThrow(TaskNotFoundException::new);
        BoardColumn target = column(ownerId, projectId, targetColumnId);
        TaskStatus nextStatus = target.getStage().taskStatus();
        enforceWip(ownerId, projectId, target, nextStatus, taskId, false);

        List<ProjectTaskPlacement> board = placements.findBoard(ownerId, projectId);
        List<ProjectTaskPlacement> source = board.stream().filter(item -> item.getColumnId().equals(current.getColumnId())).toList();
        if (current.getColumnId().equals(targetColumnId)) {
            List<ProjectTaskPlacement> ordered = new ArrayList<>(source);
            ordered.remove(current);
            insertAtVisiblePosition(ordered, current, requestedPosition);
            compactPositions(ordered);
        } else {
            List<ProjectTaskPlacement> destination = board.stream().filter(item -> item.getColumnId().equals(targetColumnId)).toList();
            List<ProjectTaskPlacement> sourceRemaining = source.stream().filter(item -> item != current).toList();
            int tempPosition = destination.stream().mapToInt(ProjectTaskPlacement::getPosition).max().orElse(-1)
                    + destination.size() + 10;
            current.move(projectId, target, tempPosition);
            placements.saveAndFlush(current);
            compactPositions(sourceRemaining);
            List<ProjectTaskPlacement> ordered = new ArrayList<>(destination);
            insertAtVisiblePosition(ordered, current, requestedPosition);
            compactPositions(ordered);
        }
        statusTransition.run();
        record(ownerId, projectId, taskId, ProjectActivityType.TASK_MOVED,
                "Task moved to “" + target.getName() + "”");
    }

    public void reassignTask(UUID ownerId, UUID sourceProjectId, UUID taskId,
            UUID destinationProjectId, UUID destinationColumnId) {
        if (sourceProjectId.equals(destinationProjectId)) throw new TaskRuleException("Choose a different destination project.");
        Task task = tasks.findLockedByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
        ProjectTaskPlacement current = placements.findPlacement(taskId, ownerId, sourceProjectId).orElseThrow(TaskNotFoundException::new);
        List<UUID> lockOrder = new ArrayList<>(List.of(sourceProjectId, destinationProjectId));
        lockOrder.sort(Comparator.comparing(UUID::toString));
        Map<UUID, Project> locked = new HashMap<>();
        for (UUID id : lockOrder) locked.put(id, lockProject(ownerId, id));
        Project source = locked.get(sourceProjectId), destination = locked.get(destinationProjectId);
        ensureWritable(source); ensureWritable(destination); validateAttachableTask(task);
        if (agileItems.existsByTaskId(taskId))
            throw new TaskRuleException("This task has Scrum history and cannot be moved to another project.");
        BoardColumn target = column(ownerId, destinationProjectId, destinationColumnId);
        enforceWip(ownerId, destinationProjectId, target, target.getStage().taskStatus(), taskId, false);

        List<ProjectTaskPlacement> sourceRemaining = placements.findBoard(ownerId, sourceProjectId).stream()
                .filter(item -> !item.getTaskId().equals(taskId) && item.getColumnId().equals(current.getColumnId())).toList();
        placements.delete(current); placements.flush(); compactPositions(sourceRemaining);
        taskService.transition(ownerId, taskId, target.getStage().taskStatus());
        int position = (int) placements.countByOwnerIdAndProjectIdAndColumnId(ownerId, destinationProjectId, target.getId());
        placements.save(ProjectTaskPlacement.create(ownerId, destinationProjectId, task, target, position));
        record(ownerId, sourceProjectId, taskId, ProjectActivityType.TASK_REMOVED, "Task moved to “" + destination.getName() + "”");
        record(ownerId, destinationProjectId, taskId, ProjectActivityType.TASK_ADDED, "Task moved from “" + source.getName() + "”");
    }

    public BoardColumn createColumn(UUID ownerId, UUID projectId, String name, BoardStage stage,
            Integer wipLimit, WipPolicy policy) {
        Project project = lockProject(ownerId, projectId); ensureWritable(project);
        validateColumnInput(name, stage, wipLimit, policy);
        List<BoardColumn> current = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId);
        if (current.size() >= MAX_COLUMNS) throw new TaskRuleException("A board can have up to 12 columns.");
        if (columns.existsByOwnerIdAndProjectIdAndNameIgnoreCase(ownerId, projectId, name.trim()))
            throw new TaskRuleException("Choose a unique column name.");
        BoardColumn saved = columns.save(BoardColumn.create(ownerId, projectId, name, stage, current.size(), wipLimit, policy));
        record(ownerId, projectId, null, ProjectActivityType.COLUMN_CREATED, "Column “" + saved.getName() + "” created");
        return saved;
    }

    public void updateColumn(UUID ownerId, UUID projectId, UUID columnId, String name,
            BoardStage stage, Integer wipLimit, WipPolicy policy) {
        Project project = lockProject(ownerId, projectId); ensureWritable(project);
        validateColumnInput(name, stage, wipLimit, policy);
        BoardColumn selected = column(ownerId, projectId, columnId);
        if (columns.existsByOwnerIdAndProjectIdAndNameIgnoreCase(ownerId, projectId, name.trim())
                && !selected.getName().equalsIgnoreCase(name.trim())) throw new TaskRuleException("Choose a unique column name.");
        List<BoardStage> stages = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId).stream()
                .map(item -> item.getId().equals(columnId) ? stage : item.getStage()).toList();
        ensureStageCoverage(stages);
        long currentWip = placements.countWorkInColumn(ownerId, projectId, columnId, WIP_EXCLUDED, null);
        if (policy == WipPolicy.STRICT && wipLimit != null && currentWip > wipLimit)
            throw new TaskRuleException("The current work in this column exceeds that strict WIP limit.");
        selected.update(name, stage, wipLimit, policy);
        record(ownerId, projectId, null, ProjectActivityType.COLUMN_UPDATED, "Column “" + name.trim() + "” updated");
    }

    public void reorderColumn(UUID ownerId, UUID projectId, UUID columnId, int direction) {
        if (direction != -1 && direction != 1) throw new TaskRuleException("Choose a valid move direction.");
        Project project = lockProject(ownerId, projectId); ensureWritable(project);
        List<BoardColumn> ordered = new ArrayList<>(columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId));
        BoardColumn selected = ordered.stream().filter(item -> item.getId().equals(columnId)).findFirst().orElseThrow(TaskNotFoundException::new);
        int index = ordered.indexOf(selected), target = index + direction;
        if (target < 0 || target >= ordered.size()) return;
        Collections.swap(ordered, index, target); compactColumns(ordered);
    }

    public void deleteColumn(UUID ownerId, UUID projectId, UUID columnId, UUID destinationColumnId) {
        Project project = lockProject(ownerId, projectId); ensureWritable(project);
        BoardColumn source = column(ownerId, projectId, columnId);
        if (columnId.equals(destinationColumnId)) throw new TaskRuleException("Choose another destination column.");
        BoardColumn destination = column(ownerId, projectId, destinationColumnId);
        List<BoardStage> remainingStages = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId).stream()
                .filter(item -> !item.getId().equals(columnId)).map(BoardColumn::getStage).toList();
        ensureStageCoverage(remainingStages);
        List<ProjectTaskPlacement> board = placements.findBoard(ownerId, projectId);
        List<ProjectTaskPlacement> sourceCards = board.stream().filter(item -> item.getColumnId().equals(columnId)).toList();
        List<ProjectTaskPlacement> destinationCards = new ArrayList<>(board.stream().filter(item -> item.getColumnId().equals(destinationColumnId)).toList());
        long incomingWip = sourceCards.stream().filter(this::countsForWip).count();
        long currentWip = destinationCards.stream().filter(this::countsForWip).count();
        if (destination.getWipPolicy() == WipPolicy.STRICT && destination.getWipLimit() != null
                && currentWip + incomingWip > destination.getWipLimit())
            throw new TaskRuleException("Move some cards first; the destination column has a strict WIP limit.");
        if (!sourceCards.isEmpty()) {
            int offset = destinationCards.stream().mapToInt(ProjectTaskPlacement::getPosition).max().orElse(-1)
                    + sourceCards.size() + destinationCards.size() + 10;
            int i = 0;
            for (ProjectTaskPlacement placement : sourceCards) placement.move(projectId, destination, offset + i++);
            placements.saveAllAndFlush(sourceCards);
            destinationCards.addAll(sourceCards);
            compactPositions(destinationCards);
            for (ProjectTaskPlacement placement : sourceCards)
                if (placement.getTask().getStatus() != destination.getStage().taskStatus())
                    taskService.transition(ownerId, placement.getTaskId(), destination.getStage().taskStatus());
        }
        columns.delete(source); columns.flush();
        List<BoardColumn> remaining = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId).stream()
                .filter(item -> !item.getId().equals(columnId)).toList();
        compactColumns(remaining);
        record(ownerId, projectId, null, ProjectActivityType.COLUMN_REMOVED, "Column “" + source.getName() + "” removed");
    }

    public ProjectMilestone createMilestone(UUID ownerId, UUID projectId, String title,
            String description, LocalDate targetDate) {
        Project project = lockProject(ownerId, projectId); ensureWritable(project);
        validateMilestoneInput(title, description);
        List<ProjectMilestone> current = milestones.findAllByProjectIdOrderByPositionAsc(projectId);
        if (current.size() >= MAX_MILESTONES) throw new TaskRuleException("A project can have up to 50 milestones.");
        if (targetDate != null && project.getStartDate() != null && targetDate.isBefore(project.getStartDate()))
            throw new TaskRuleException("The milestone date must be on or after the project start date.");
        ProjectMilestone saved = milestones.save(ProjectMilestone.create(projectId, title, description, targetDate, current.size(), Instant.now()));
        record(ownerId, projectId, null, ProjectActivityType.MILESTONE_CREATED, "Milestone “" + saved.getTitle() + "” created");
        return saved;
    }

    public void updateMilestone(UUID ownerId, UUID projectId, UUID milestoneId,
            String title, String description, LocalDate targetDate) {
        Project project = lockProject(ownerId, projectId); ensureWritable(project);
        validateMilestoneInput(title, description);
        if (targetDate != null && project.getStartDate() != null && targetDate.isBefore(project.getStartDate()))
            throw new TaskRuleException("The milestone date must be on or after the project start date.");
        ProjectMilestone milestone = milestone(projectId, milestoneId);
        milestone.update(title, description, targetDate, Instant.now());
        record(ownerId, projectId, null, ProjectActivityType.MILESTONE_UPDATED, "Milestone “" + title.trim() + "” updated");
    }

    public void transitionMilestone(UUID ownerId, UUID projectId, UUID milestoneId, MilestoneStatus status) {
        Project project = lockProject(ownerId, projectId); ensureWritable(project);
        if (status == null) throw new TaskRuleException("Choose a milestone status.");
        ProjectMilestone milestone = milestone(projectId, milestoneId);
        try { milestone.transition(status, Instant.now()); }
        catch (IllegalArgumentException ex) { throw new TaskRuleException(ex.getMessage()); }
        record(ownerId, projectId, null, ProjectActivityType.MILESTONE_STATUS_CHANGED,
                "Milestone “" + milestone.getTitle() + "” marked " + status.name().toLowerCase(Locale.ROOT));
    }

    public void deleteMilestone(UUID ownerId, UUID projectId, UUID milestoneId) {
        Project project = lockProject(ownerId, projectId); ensureWritable(project);
        ProjectMilestone milestone = milestone(projectId, milestoneId);
        List<ProjectTaskPlacement> linked = placements.findAllByOwnerIdAndProjectIdAndMilestoneId(ownerId, projectId, milestoneId);
        linked.forEach(item -> item.setMilestoneId(null)); placements.saveAllAndFlush(linked);
        milestones.delete(milestone); milestones.flush();
        record(ownerId, projectId, null, ProjectActivityType.MILESTONE_REMOVED, "Milestone “" + milestone.getTitle() + "” removed");
    }

    public void linkMilestone(UUID ownerId, UUID projectId, UUID taskId, UUID milestoneId) {
        tasks.findLockedByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
        Project project = lockProject(ownerId, projectId); ensureWritable(project);
        ProjectTaskPlacement placement = placements.findPlacement(taskId, ownerId, projectId).orElseThrow(TaskNotFoundException::new);
        ProjectMilestone milestone = milestoneId == null ? null : milestone(projectId, milestoneId);
        placement.setMilestoneId(milestone == null ? null : milestone.getId());
        record(ownerId, projectId, taskId, ProjectActivityType.TASK_MILESTONE_CHANGED,
                milestone == null ? "Task removed from its milestone" : "Task added to milestone “" + milestone.getTitle() + "”");
    }

    public ProjectTemplate saveTemplate(UUID ownerId, UUID projectId, String name,
            String description, boolean includeTasks) {
        Project project = lockProject(ownerId, projectId);
        validateTemplateText(name, description);
        if (templates.existsByOwner_IdAndNameIgnoreCase(ownerId, name.trim()))
            throw new TaskRuleException("Choose a template name you have not used.");
        List<BoardColumn> boardColumns = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId);
        List<ProjectMilestone> projectMilestones = milestones.findAllByProjectIdOrderByPositionAsc(projectId);
        List<ProjectTaskPlacement> board = placements.findBoard(ownerId, projectId).stream()
                .filter(item -> item.getTask().getArchivedAt() == null).toList();
        List<ProjectTemplateDefinition.Column> columnBlueprints = boardColumns.stream()
                .map(column -> new ProjectTemplateDefinition.Column(column.getName(), column.getStage(),
                        column.getPosition(), column.getWipLimit(), column.getWipPolicy())).toList();
        List<ProjectTemplateDefinition.Milestone> milestoneBlueprints = projectMilestones.stream()
                .map(item -> new ProjectTemplateDefinition.Milestone(item.getTitle(), item.getDescription(),
                        dayOffset(project.getStartDate(), item.getTargetDate()), item.getPosition())).toList();
        List<ProjectTemplateDefinition.TaskBlueprint> taskBlueprints = List.of();
        if (includeTasks) {
            if (board.size() > MAX_TEMPLATE_TASKS) throw new TaskRuleException("Choose a project with no more than 100 tasks to save task examples.");
            Map<UUID, Integer> milestonePositions = new HashMap<>();
            projectMilestones.forEach(item -> milestonePositions.put(item.getId(), item.getPosition()));
            taskBlueprints = board.stream().map(item -> new ProjectTemplateDefinition.TaskBlueprint(
                    item.getTask().getTitle(), item.getTask().getDescription(), item.getTask().getPriority(),
                    item.getColumn().getPosition(), item.getMilestoneId() == null ? null : milestonePositions.get(item.getMilestoneId()),
                    dayOffset(project.getStartDate(), item.getTask().getDueDate()))).toList();
        }
        ProjectTemplateDefinition definition = new ProjectTemplateDefinition(1, columnBlueprints, milestoneBlueprints, taskBlueprints);
        validateDefinition(definition);
        try {
            ProjectTemplate template = templates.save(ProjectTemplate.create(user(ownerId), name,
                    description, objectMapper.writeValueAsString(definition), 1, Instant.now()));
            record(ownerId, projectId, null, ProjectActivityType.TEMPLATE_SAVED, "Reusable template “" + name.trim() + "” saved");
            return template;
        } catch (JacksonException ex) {
            throw new TaskRuleException("The project structure could not be saved as a template.");
        }
    }

    public void deleteTemplate(UUID ownerId, UUID templateId) {
        templates.delete(template(ownerId, templateId));
    }

    @Transactional(readOnly = true)
    public List<Task> availableTasks(UUID ownerId) {
        // shortcut: project pickers show 250 available tasks, paginate when users outgrow this limit.
        return tasks.findAvailableProjectTasks(ownerId, PageRequest.of(0, MAX_BOARD_TASKS));
    }

    @Transactional(readOnly = true)
    public List<BoardColumn> columns(UUID ownerId, UUID projectId) {
        project(ownerId, projectId);
        return columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId);
    }

    @Transactional(readOnly = true)
    public List<BoardColumn> sharedColumns(UUID actorId, UUID projectId) {
        Project project = projectAccess.require(actorId, projectId, WorkspacePermission.VIEW);
        return columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(project.getOwner().getId(), projectId);
    }

    @Transactional(readOnly = true)
    public List<ProjectBoardColumnView> board(UUID ownerId, UUID projectId, TaskPriority priority,
            TaskStatus status, UUID tagId, UUID milestoneId, LocalDate dueBefore, LocalDate dueAfter) {
        return board(project(ownerId, projectId), priority, status, tagId, milestoneId, dueBefore, dueAfter);
    }

    @Transactional(readOnly = true)
    public List<ProjectBoardColumnView> sharedBoard(UUID actorId, UUID projectId) {
        Project project = projectAccess.require(actorId, projectId, WorkspacePermission.VIEW);
        return board(project, null, null, null, null, null, null);
    }

    private List<ProjectBoardColumnView> board(Project project, TaskPriority priority,
            TaskStatus status, UUID tagId, UUID milestoneId, LocalDate dueBefore, LocalDate dueAfter) {
        UUID ownerId = project.getOwner().getId();
        UUID projectId = project.getId();
        List<ProjectTaskPlacement> all = placements.findBoard(ownerId, projectId).stream()
                .filter(item -> item.getTask().getArchivedAt() == null)
                .filter(item -> priority == null || item.getTask().getPriority() == priority)
                .filter(item -> status == null || item.getTask().getStatus() == status)
                .filter(item -> tagId == null || item.getTask().getTags().stream().anyMatch(tag -> tag.getId().equals(tagId)))
                .filter(item -> milestoneId == null || Objects.equals(item.getMilestoneId(), milestoneId))
                .filter(item -> dueBefore == null || item.getTask().getDueDate() != null && !item.getTask().getDueDate().isAfter(dueBefore))
                .filter(item -> dueAfter == null || item.getTask().getDueDate() != null && !item.getTask().getDueDate().isBefore(dueAfter))
                .toList();
        List<BoardColumn> boardColumns = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId);
        List<ProjectBoardColumnView> result = new ArrayList<>();
        for (BoardColumn column : boardColumns) {
            List<ProjectTaskPlacement> cards = all.stream().filter(item -> item.getColumnId().equals(column.getId())).toList();
            long wip = cards.stream().filter(this::countsForWip).count();
            result.add(new ProjectBoardColumnView(column, cards, wip,
                    column.getWipLimit() != null && wip > column.getWipLimit()));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public List<ProjectMilestone> milestones(UUID ownerId, UUID projectId) {
        project(ownerId, projectId);
        return milestones.findAllByProjectIdOrderByPositionAsc(projectId);
    }

    @Transactional(readOnly = true)
    public List<MilestoneProgress> milestoneProgress(UUID ownerId, UUID projectId) {
        List<ProjectTaskPlacement> board = placements.findBoard(ownerId, projectId);
        return milestones(ownerId, projectId).stream().map(milestone -> {
            List<Task> eligible = board.stream().filter(item -> Objects.equals(item.getMilestoneId(), milestone.getId()))
                    .map(ProjectTaskPlacement::getTask).filter(task -> task.getArchivedAt() == null && task.getStatus() != TaskStatus.CANCELLED).toList();
            long completed = eligible.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
            return new MilestoneProgress(milestone, eligible.size(), completed,
                    eligible.isEmpty() ? 0 : (int) Math.round(100.0 * completed / eligible.size()));
        }).toList();
    }

    @Transactional(readOnly = true)
    public ProjectProgress progress(UUID ownerId, UUID projectId) {
        Project project = project(ownerId, projectId);
        List<Task> eligible = placements.findBoard(ownerId, projectId).stream().map(ProjectTaskPlacement::getTask)
                .filter(task -> task.getParentId() == null && task.getArchivedAt() == null && task.getStatus() != TaskStatus.CANCELLED).toList();
        long completed = eligible.stream().filter(task -> task.getStatus() == TaskStatus.COMPLETED).count();
        long open = eligible.stream().filter(task -> !WIP_EXCLUDED.contains(task.getStatus())).count();
        LocalDate today = today(project.getOwner());
        long overdue = eligible.stream().filter(task -> !WIP_EXCLUDED.contains(task.getStatus())
                && task.getDueDate() != null && task.getDueDate().isBefore(today)).count();
        EnumMap<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus taskStatus : TaskStatus.values())
            counts.put(taskStatus, eligible.stream().filter(task -> task.getStatus() == taskStatus).count());
        List<ProjectMilestone> projectMilestones = milestones.findAllByProjectIdOrderByPositionAsc(projectId);
        long milestoneCount = projectMilestones.size();
        long completedMilestones = projectMilestones.stream()
                .filter(item -> item.getStatus() == MilestoneStatus.COMPLETED).count();
        String health = overdue > 0 ? "Overdue tasks exist" : project.getTargetEndDate() == null ? "No target date"
                : eligible.isEmpty() ? "No active tasks" : "No overdue tasks";
        int percent = eligible.isEmpty() ? 0 : (int) Math.round(100.0 * completed / eligible.size());
        Map<String, Long> statusCounts = new LinkedHashMap<>();
        counts.forEach((key, value) -> statusCounts.put(key.name(), value));
        return new ProjectProgress(eligible.size(), completed, open, overdue, percent,
                milestoneCount, completedMilestones, health, statusCounts);
    }

    @Transactional(readOnly = true)
    public List<ProjectActivity> recentActivity(UUID ownerId, UUID projectId) {
        project(ownerId, projectId);
        return activity.findAllByOwnerIdAndProjectIdOrderByOccurredAtDesc(ownerId, projectId, PageRequest.of(0, 30));
    }

    @Transactional(readOnly = true)
    public long activeProjectCount(UUID ownerId) {
        return projects.countByOwner_IdAndArchivedAtIsNullAndStatusIn(ownerId,
                List.of(ProjectStatus.PLANNED, ProjectStatus.ACTIVE, ProjectStatus.ON_HOLD));
    }

    @EventListener
    public void synchronizeTaskStatus(TaskStatusChangedEvent event) {
        Optional<ProjectTaskPlacement> found = event.workspaceId() == null
                ? placements.findByTaskIdAndOwnerId(event.taskId(), event.ownerId())
                : placements.findByTaskIdAndWorkspaceId(event.taskId(), event.workspaceId());
        if (found.isEmpty()) return;
        ProjectTaskPlacement placement = found.get();
        UUID ownerId = placement.getOwnerId();
        Project project = event.workspaceId() == null
                ? lockProject(ownerId, placement.getProjectId())
                : projects.findLockedByIdAndWorkspace(placement.getProjectId(), event.workspaceId())
                        .orElseThrow(TaskNotFoundException::new);
        BoardColumn current = column(ownerId, project.getId(), placement.getColumnId());
        if (current.getStage().taskStatus() == event.status()) return;
        BoardColumn target = columnForStatus(ownerId, project.getId(), event.status());
        enforceWip(ownerId, project.getId(), target, event.status(), event.taskId(), project.getArchivedAt() != null);
        List<ProjectTaskPlacement> board = placements.findBoard(ownerId, project.getId());
        List<ProjectTaskPlacement> source = board.stream().filter(item -> item.getColumnId().equals(current.getId())
                && !item.getTaskId().equals(event.taskId())).toList();
        List<ProjectTaskPlacement> destination = new ArrayList<>(board.stream().filter(item -> item.getColumnId().equals(target.getId())
                && !item.getTaskId().equals(event.taskId())).toList());
        if (!current.getId().equals(target.getId())) {
            int temp = destination.stream().mapToInt(ProjectTaskPlacement::getPosition).max().orElse(-1) + destination.size() + 10;
            placement.move(project.getId(), target, temp); placements.saveAndFlush(placement);
            compactPositions(source);
            destination.add(placement); compactPositions(destination);
        }
        record(ownerId, project.getId(), event.taskId(), ProjectActivityType.TASK_STATUS_SYNCED,
                "Task status changed to " + event.status().name() + " and the board was synchronized");
    }

    @EventListener
    public void recordTaskActivity(TaskActivityRecordedEvent event) {
        ProjectActivityType type = switch (event.type()) {
            case TASK_UPDATED -> ProjectActivityType.TASK_UPDATED;
            case TASK_ARCHIVED -> ProjectActivityType.TASK_ARCHIVED;
            case TASK_RESTORED -> ProjectActivityType.TASK_RESTORED;
            case TASK_DELETED -> ProjectActivityType.TASK_DELETED;
            default -> null;
        };
        if (type == null) return;
        placements.findByTaskIdAndOwnerId(event.taskId(), event.ownerId()).ifPresent(placement ->
                record(event.ownerId(), placement.getProjectId(), event.taskId(), type, event.summary()));
    }

    private void enforceWip(UUID ownerId, UUID projectId, BoardColumn column, TaskStatus nextStatus,
            UUID taskId, boolean archivedProject) {
        if (archivedProject || column.getWipLimit() == null || column.getWipPolicy() != WipPolicy.STRICT
                || WIP_EXCLUDED.contains(nextStatus)) return;
        long count = placements.countWorkInColumn(ownerId, projectId, column.getId(), WIP_EXCLUDED, taskId);
        if (count >= column.getWipLimit()) throw new TaskRuleException("“" + column.getName() + "” is at its strict WIP limit.");
    }

    private BoardColumn columnForStatus(UUID ownerId, UUID projectId, TaskStatus status) {
        List<BoardColumn> found = columns.findAllByOwnerIdAndProjectIdOrderByPositionAsc(ownerId, projectId);
        BoardStage preferred = BoardStage.preferredFor(status);
        return found.stream().filter(item -> item.getStage() == preferred).findFirst()
                .or(() -> found.stream().filter(item -> item.getStage().taskStatus() == status).findFirst())
                .orElseThrow(() -> new TaskRuleException("The project workflow has no column for " + status.name() + "."));
    }

    private void compactPositions(List<ProjectTaskPlacement> ordered) {
        if (ordered.isEmpty()) return;
        int offset = ordered.stream().mapToInt(ProjectTaskPlacement::getPosition).max().orElse(0) + ordered.size() + 10;
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(offset + i);
        placements.saveAllAndFlush(ordered);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).setPosition(i);
        placements.saveAllAndFlush(ordered);
    }

    private void insertAtVisiblePosition(List<ProjectTaskPlacement> ordered, ProjectTaskPlacement placement, int position) {
        List<ProjectTaskPlacement> visible = ordered.stream()
                .filter(item -> item.getTask().getArchivedAt() == null).toList();
        int index = Math.max(0, Math.min(position, visible.size()));
        ordered.add(index == visible.size() ? ordered.size() : ordered.indexOf(visible.get(index)), placement);
    }

    private void compactColumns(List<BoardColumn> ordered) {
        if (ordered.isEmpty()) return;
        int offset = ordered.stream().mapToInt(BoardColumn::getPosition).max().orElse(0) + ordered.size() + 10;
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).move(offset + i);
        columns.saveAllAndFlush(ordered);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).move(i);
        columns.saveAllAndFlush(ordered);
    }

    private boolean countsForWip(ProjectTaskPlacement placement) {
        Task task = placement.getTask();
        return task.getArchivedAt() == null && !WIP_EXCLUDED.contains(task.getStatus());
    }

    private void validateProjectInput(ProjectForm form) {
        if (form == null || form.getName() == null || form.getName().isBlank() || form.getName().trim().length() > 160)
            throw new TaskRuleException("Enter a project name of 1 to 160 characters.");
        if (form.getDescription() != null && form.getDescription().length() > 2000)
            throw new TaskRuleException("The project description must be 2,000 characters or fewer.");
        if (form.getStatus() == null) throw new TaskRuleException("Choose a project status.");
        if (form.getStartDate() != null && form.getTargetEndDate() != null && form.getTargetEndDate().isBefore(form.getStartDate()))
            throw new TaskRuleException("The target date must be on or after the start date.");
        if (form.getColor() == null || !form.getColor().matches("#[0-9a-fA-F]{6}"))
            throw new TaskRuleException("Choose a valid six-digit project color.");
    }

    private void validateAttachableTask(Task task) {
        if (task.getArchivedAt() != null) throw new TaskRuleException("Restore this task before adding it to a project.");
        if (task.getParentId() != null) throw new TaskRuleException("Add a main task to the project; subtasks stay with their parent.");
    }

    private void validateTaskInput(String title, String description) {
        if (title == null || title.isBlank() || title.trim().length() > 160)
            throw new TaskRuleException("Enter a task title of 1 to 160 characters.");
        if (description != null && description.length() > 6000)
            throw new TaskRuleException("The task description must be 6,000 characters or fewer.");
    }

    private void validateColumnInput(String name, BoardStage stage, Integer wipLimit, WipPolicy policy) {
        if (name == null || name.isBlank() || name.trim().length() > 48)
            throw new TaskRuleException("Enter a column name of 1 to 48 characters.");
        if (stage == null) throw new TaskRuleException("Choose a workflow category.");
        if (wipLimit != null && (wipLimit < 1 || wipLimit > 500))
            throw new TaskRuleException("A WIP limit must be between 1 and 500.");
        if (wipLimit == null && policy != null && policy != WipPolicy.NONE)
            throw new TaskRuleException("Choose a WIP limit before setting its policy.");
        if (wipLimit != null && (policy == null || policy == WipPolicy.NONE))
            throw new TaskRuleException("Choose Advisory or Strict for a configured WIP limit.");
    }

    private void validateMilestoneInput(String title, String description) {
        if (title == null || title.isBlank() || title.trim().length() > 160)
            throw new TaskRuleException("Enter a milestone title of 1 to 160 characters.");
        if (description != null && description.length() > 1200)
            throw new TaskRuleException("The milestone description must be 1,200 characters or fewer.");
    }

    private void validateTemplateText(String name, String description) {
        if (name == null || name.isBlank() || name.trim().length() > 100)
            throw new TaskRuleException("Enter a template name of 1 to 100 characters.");
        if (description != null && description.length() > 1200)
            throw new TaskRuleException("The template description must be 1,200 characters or fewer.");
    }

    private void validateColumns(List<ProjectTemplateDefinition.Column> definition) {
        if (definition == null || definition.isEmpty() || definition.size() > MAX_COLUMNS)
            throw new TaskRuleException("A workflow needs 1 to 12 columns.");
        Set<String> names = new HashSet<>();
        for (ProjectTemplateDefinition.Column item : definition) {
            validateColumnInput(item.name(), item.stage(), item.wipLimit(), item.wipPolicy());
            if (!names.add(item.name().trim().toLowerCase(Locale.ROOT))) throw new TaskRuleException("Workflow column names must be unique.");
        }
        ensureStageCoverage(definition.stream().map(ProjectTemplateDefinition.Column::stage).toList());
    }

    private void ensureStageCoverage(List<BoardStage> stages) {
        if (!stages.contains(BoardStage.TODO) && !stages.contains(BoardStage.BACKLOG))
            throw new TaskRuleException("Keep a Backlog or To Do column for new tasks.");
        if (!stages.contains(BoardStage.IN_PROGRESS) && !stages.contains(BoardStage.IN_REVIEW))
            throw new TaskRuleException("Keep an In Progress or In Review column.");
        for (BoardStage stage : List.of(BoardStage.ON_HOLD, BoardStage.DONE, BoardStage.CANCELLED))
            if (!stages.contains(stage)) throw new TaskRuleException("Keep a " + stage.name().replace('_', ' ') + " column.");
    }

    private List<ProjectTemplateDefinition.Column> defaultColumns() {
        List<ProjectTemplateDefinition.Column> result = new ArrayList<>();
        String[] names = { "Backlog", "To Do", "In Progress", "In Review", "On Hold", "Done", "Cancelled" };
        for (int i = 0; i < DEFAULT_STAGES.size(); i++) {
            BoardStage stage = DEFAULT_STAGES.get(i);
            Integer wip = stage == BoardStage.IN_PROGRESS ? 3 : null;
            WipPolicy policy = wip == null ? WipPolicy.NONE : WipPolicy.ADVISORY;
            result.add(new ProjectTemplateDefinition.Column(names[i], stage, i, wip, policy));
        }
        return result;
    }

    private void validateDefinition(ProjectTemplateDefinition definition) {
        if (definition == null || definition.version() != 1) throw new TaskRuleException("This template version is unsupported.");
        validateColumns(definition.columns());
        if (definition.milestones() == null || definition.milestones().size() > MAX_MILESTONES)
            throw new TaskRuleException("A template can contain up to 50 milestone definitions.");
        if (definition.tasks() == null || definition.tasks().size() > MAX_TEMPLATE_TASKS)
            throw new TaskRuleException("A template can contain up to 100 task examples.");
        for (ProjectTemplateDefinition.Milestone milestone : definition.milestones()) {
            validateMilestoneInput(milestone.title(), milestone.description());
            if (milestone.dueInDays() != null && Math.abs((long) milestone.dueInDays()) > 3650)
                throw new TaskRuleException("Template milestone offsets must be within ten years.");
        }
        for (ProjectTemplateDefinition.TaskBlueprint task : definition.tasks()) {
            validateTaskInput(task.title(), task.description());
            if (task.columnPosition() < 0 || task.columnPosition() >= definition.columns().size())
                throw new TaskRuleException("A template task refers to a missing column.");
            if (task.milestonePosition() != null && definition.milestones().stream().noneMatch(m -> m.position() == task.milestonePosition()))
                throw new TaskRuleException("A template task refers to a missing milestone.");
            if (task.dueInDays() != null && Math.abs((long) task.dueInDays()) > 3650)
                throw new TaskRuleException("Template task date offsets must be within ten years.");
        }
    }

    private ProjectTemplateDefinition definition(ProjectTemplate template) {
        if (template.getSchemaVersion() != 1) throw new TaskRuleException("This template version is unsupported.");
        try {
            ProjectTemplateDefinition definition = objectMapper.readValue(template.getDefinitionJson(), ProjectTemplateDefinition.class);
            validateDefinition(definition);
            return definition;
        } catch (JacksonException ex) {
            throw new TaskRuleException("This template definition is invalid. Delete it and save a new one.");
        }
    }

    private LocalDate targetDate(LocalDate startDate, Integer offset) {
        return startDate == null || offset == null ? null : startDate.plusDays(offset);
    }

    private Integer dayOffset(LocalDate start, LocalDate target) {
        if (start == null || target == null) return null;
        long days = ChronoUnit.DAYS.between(start, target);
        if (Math.abs(days) > 3650) return null;
        return (int) days;
    }

    private Project project(UUID ownerId, UUID projectId) {
        return projects.findByIdAndOwner_Id(projectId, ownerId).orElseThrow(TaskNotFoundException::new);
    }

    @Transactional(readOnly = true)
    public Project getProject(UUID ownerId, UUID projectId) { return project(ownerId, projectId); }

    private Project lockProject(UUID ownerId, UUID projectId) {
        return projects.findLockedByIdAndOwner(projectId, ownerId).orElseThrow(TaskNotFoundException::new);
    }

    private void ensureWritable(Project project) {
        if (project.getArchivedAt() != null) throw new TaskRuleException("Restore this project before changing it.");
    }

    private BoardColumn column(UUID ownerId, UUID projectId, UUID columnId) {
        return columns.findByIdAndOwnerIdAndProjectId(columnId, ownerId, projectId).orElseThrow(TaskNotFoundException::new);
    }

    private ProjectMilestone milestone(UUID projectId, UUID milestoneId) {
        return milestones.findByIdAndProjectId(milestoneId, projectId).orElseThrow(TaskNotFoundException::new);
    }

    private ProjectTemplate template(UUID ownerId, UUID templateId) {
        return templates.findByIdAndOwner_Id(templateId, ownerId).orElseThrow(TaskNotFoundException::new);
    }

    private UserAccount user(UUID ownerId) {
        return users.findById(ownerId).orElseThrow(TaskNotFoundException::new);
    }

    private LocalDate today(UserAccount owner) {
        try { return LocalDate.now(ZoneId.of(owner.getTimezone())); }
        catch (RuntimeException ex) { return LocalDate.now(ZoneId.of("UTC")); }
    }

    private void record(UUID ownerId, UUID projectId, UUID taskId, ProjectActivityType type, String summary) {
        activity.save(ProjectActivity.create(ownerId, projectId, taskId, type, summary, Instant.now()));
    }
}
