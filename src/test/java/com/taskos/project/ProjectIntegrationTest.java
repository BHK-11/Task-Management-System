package com.taskos.project;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskos.auth.service.RegistrationService;
import com.taskos.project.dto.ProjectForm;
import com.taskos.project.entity.*;
import com.taskos.project.repository.ProjectActivityRepository;
import com.taskos.project.repository.ProjectRepository;
import com.taskos.project.repository.ProjectTaskPlacementRepository;
import com.taskos.project.service.ProjectService;
import com.taskos.security.service.DatabaseUserDetailsService;
import com.taskos.scheduling.dto.ProjectSchedulePage;
import com.taskos.scheduling.dto.ScheduleChangePreview;
import com.taskos.scheduling.entity.TaskDependencyType;
import com.taskos.scheduling.repository.ScheduleBaselineRepository;
import com.taskos.scheduling.repository.TaskDependencyRepository;
import com.taskos.scheduling.service.ProjectScheduleService;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskRuleException;
import com.taskos.task.service.TaskService;
import java.util.UUID;
import java.time.LocalDate;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class ProjectIntegrationTest {
    private static final String PASSWORD = "a sufficiently long test passphrase";
    @Autowired private MockMvc mvc;
    @Autowired private RegistrationService registration;
    @Autowired private DatabaseUserDetailsService userDetails;
    @Autowired private ProjectService projects;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private ProjectTaskPlacementRepository placements;
    @Autowired private ProjectActivityRepository activity;
    @Autowired private TaskService taskService;
    @Autowired private TaskRepository tasks;
    @Autowired private ProjectScheduleService schedules;
    @Autowired private ScheduleBaselineRepository baselines;
    @Autowired private TaskDependencyRepository dependencies;
    @Autowired private JdbcTemplate jdbc;

    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void createUsers() { alice = createUser("Alice"); bob = createUser("Bob"); }

    @Test
    void pagesRenderRequireAuthenticationAndEnforceProjectOwnershipAndCsrf() throws Exception {
        UUID projectId = createProject(alice, "Private launch");
        mvc.perform(get("/projects").with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Private launch")));
        for (String suffix : new String[] { "", "/board", "/milestones", "/activity", "/settings" })
            mvc.perform(get("/projects/" + projectId + suffix).with(user(alice.principal()))).andExpect(status().isOk());
        mvc.perform(get("/projects/" + projectId).with(user(bob.principal()))).andExpect(status().isNotFound());
        mvc.perform(get("/projects")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/projects").with(user(alice.principal())).param("name", "Missing token"))
                .andExpect(status().isForbidden());
    }

    @Test
    void projectTaskPlacementUsesCanonicalTaskAndStatusChangesKeepBoardInSync() throws Exception {
        UUID projectId = createProject(alice, "Launch");
        BoardColumn todo = column(projectId, BoardStage.TODO);
        BoardColumn doing = column(projectId, BoardStage.IN_PROGRESS);
        UUID taskId = projects.createTask(alice.id(), projectId, todo.getId(), "Write release notes", "", TaskPriority.HIGH, null);
        assertEquals(1, tasks.countByOwner_Id(alice.id()));
        assertEquals("Write release notes", tasks.findById(taskId).orElseThrow().getTitle());
        assertEquals(todo.getId(), placements.findByTaskIdAndOwnerId(taskId, alice.id()).orElseThrow().getColumnId());

        projects.moveTask(alice.id(), projectId, taskId, doing.getId(), 0);
        assertEquals(TaskStatus.IN_PROGRESS, tasks.findById(taskId).orElseThrow().getStatus());
        assertEquals(doing.getId(), placements.findByTaskIdAndOwnerId(taskId, alice.id()).orElseThrow().getColumnId());
        taskService.transition(alice.id(), taskId, TaskStatus.COMPLETED);
        assertEquals(column(projectId, BoardStage.DONE).getId(), placements.findByTaskIdAndOwnerId(taskId, alice.id()).orElseThrow().getColumnId());
        assertTrue(activity.findAllByOwnerIdAndProjectIdOrderByOccurredAtDesc(alice.id(), projectId,
                org.springframework.data.domain.PageRequest.of(0, 30)).stream()
                .anyMatch(event -> event.getEventType() == ProjectActivityType.TASK_STATUS_SYNCED));
        Task task = tasks.findDetailByIdAndOwner(taskId, alice.id()).orElseThrow();
        com.taskos.task.dto.TaskForm form = new com.taskos.task.dto.TaskForm();
        form.setTitle("Release notes reviewed"); form.setDescription(task.getDescription()); form.setStatus(task.getStatus());
        form.setPriority(task.getPriority()); form.setStartDate(task.getStartDate()); form.setDueDate(task.getDueDate());
        form.setVersion(task.getVersion()); taskService.update(alice.id(), taskId, form);
        assertTrue(activity.findAllByOwnerIdAndProjectIdOrderByOccurredAtDesc(alice.id(), projectId,
                org.springframework.data.domain.PageRequest.of(0, 30)).stream()
                .anyMatch(event -> event.getEventType() == ProjectActivityType.TASK_UPDATED));
        mvc.perform(get("/projects/{id}/board", projectId).with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Release notes reviewed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Move to")));
    }

    @Test
    void strictWipLimitRejectsExtraCardsAndMilestoneProgressUsesAssignedTasks() {
        UUID projectId = createProject(alice, "WIP project");
        BoardColumn doing = column(projectId, BoardStage.IN_PROGRESS);
        projects.updateColumn(alice.id(), projectId, doing.getId(), doing.getName(), doing.getStage(), 1, WipPolicy.STRICT);
        UUID milestoneId = projects.createMilestone(alice.id(), projectId, "Beta", "", null).getId();
        UUID first = projects.createTask(alice.id(), projectId, doing.getId(), "Build beta", "", TaskPriority.MEDIUM, null);
        projects.linkMilestone(alice.id(), projectId, first, milestoneId);
        assertThrows(TaskRuleException.class,
                () -> projects.createTask(alice.id(), projectId, doing.getId(), "Extra work", "", TaskPriority.LOW, null));
        taskService.transition(alice.id(), first, TaskStatus.COMPLETED);
        assertEquals(100, projects.milestoneProgress(alice.id(), projectId).getFirst().percent());
    }

    @Test
    void deletingColumnSynchronizesCanonicalTaskStatusWithDestinationStage() {
        UUID projectId = createProject(alice, "Column status consistency");
        BoardColumn todo = column(projectId, BoardStage.TODO);
        BoardColumn done = column(projectId, BoardStage.DONE);
        UUID taskId = projects.createTask(alice.id(), projectId, todo.getId(), "Move when column is removed", "", TaskPriority.MEDIUM, null);

        projects.deleteColumn(alice.id(), projectId, todo.getId(), done.getId());

        assertEquals(TaskStatus.COMPLETED, tasks.findById(taskId).orElseThrow().getStatus());
        assertEquals(done.getId(), placements.findByTaskIdAndOwnerId(taskId, alice.id()).orElseThrow().getColumnId());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void concurrentMovesCannotExceedStrictWipLimit() throws Exception {
        UUID projectId = createProject(alice, "Concurrent WIP");
        BoardColumn todo = column(projectId, BoardStage.TODO);
        BoardColumn doing = column(projectId, BoardStage.IN_PROGRESS);
        projects.updateColumn(alice.id(), projectId, doing.getId(), doing.getName(), doing.getStage(), 1, WipPolicy.STRICT);
        UUID first = projects.createTask(alice.id(), projectId, todo.getId(), "First concurrent card", "", TaskPriority.MEDIUM, null);
        UUID second = projects.createTask(alice.id(), projectId, todo.getId(), "Second concurrent card", "", TaskPriority.MEDIUM, null);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var moveFirst = workers.submit(() -> concurrentMove(ready, start, projectId, first, doing.getId()));
            var moveSecond = workers.submit(() -> concurrentMove(ready, start, projectId, second, doing.getId()));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            assertEquals(1, moveFirst.get(10, TimeUnit.SECONDS) + moveSecond.get(10, TimeUnit.SECONDS));
            assertEquals(1, projects.board(alice.id(), projectId, null, null, null, null, null, null).stream()
                    .filter(view -> view.column().getId().equals(doing.getId())).findFirst().orElseThrow().wipCount());
        } finally {
            workers.shutdownNow();
            deleteConcurrentTestUsers();
        }
    }

    private int concurrentMove(CountDownLatch ready, CountDownLatch start, UUID projectId, UUID taskId, UUID columnId)
            throws InterruptedException {
        ready.countDown(); start.await();
        try { projects.moveTask(alice.id(), projectId, taskId, columnId, 0); return 1; }
        catch (TaskRuleException ex) { return 0; }
    }

    @Test
    void projectTemplateCreatesNewTasksAndDeletingProjectPreservesTasks() {
        UUID firstProject = createProject(alice, "Source project");
        BoardColumn todo = column(firstProject, BoardStage.TODO);
        UUID sourceTask = projects.createTask(alice.id(), firstProject, todo.getId(), "Reusable task", "Example", TaskPriority.HIGH, null);
        projects.createMilestone(alice.id(), firstProject, "First checkpoint", "", null);
        ProjectTemplate template = projects.saveTemplate(alice.id(), firstProject, "Launch template", "Reusable structure", true);

        ProjectForm form = projectForm("New from template");
        UUID secondProject = projects.create(alice.id(), form, template.getId());
        Task createdFromTemplate = placements.findBoard(alice.id(), secondProject).getFirst().getTask();
        assertNotEquals(sourceTask, createdFromTemplate.getId());
        assertEquals(2, tasks.countByOwner_Id(alice.id()));
        assertEquals(1, projects.milestones(alice.id(), secondProject).size());

        projects.delete(alice.id(), firstProject, true);
        assertTrue(projectRepository.findByIdAndOwner_Id(firstProject, alice.id()).isEmpty());
        assertTrue(tasks.findDetailByIdAndOwner(sourceTask, alice.id()).isPresent());
        assertTrue(placements.findByTaskIdAndOwnerId(sourceTask, alice.id()).isEmpty());
    }

    @Test
    void existingTaskCannotBeAttachedTwiceOrAcrossOwners() {
        UUID projectId = createProject(alice, "Association");
        UUID taskId = task("Standalone", alice);
        projects.addExistingTask(alice.id(), projectId, taskId);
        assertThrows(TaskRuleException.class, () -> projects.addExistingTask(alice.id(), projectId, taskId));
        assertThrows(RuntimeException.class, () -> projects.addExistingTask(bob.id(), projectId, taskId));
        assertEquals(projectId, placements.findByTaskIdAndOwnerId(taskId, alice.id()).orElseThrow().getProjectId());
    }

    @Test
    void projectFormsUseCsrfAndTemplateRoutesRender() throws Exception {
        mvc.perform(get("/projects/new").with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Create a project")));
        UUID projectId = createProject(alice, "Template source");
        projects.saveTemplate(alice.id(), projectId, "Template preview", "", false);
        mvc.perform(get("/projects/templates").with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Template preview")));
        ProjectTemplate template = projects.templates(alice.id()).getFirst();
        mvc.perform(get("/projects/templates").param("preview", template.getId().toString()).with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Workflow columns")));
        mvc.perform(post("/projects").with(user(alice.principal())).with(csrf())
                        .param("name", "Created through form").param("status", "PLANNED").param("color", "#4f46e5"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void projectSchedulePersistsDependenciesPreviewsChangesAndKeepsBaselineSnapshot() throws Exception {
        LocalDate start = LocalDate.of(2026, 1, 5);
        ProjectForm form = projectForm("Schedule project"); form.setStartDate(start);
        UUID projectId = projects.create(alice.id(), form, null);
        BoardColumn todo = column(projectId, BoardStage.TODO);
        UUID first = projects.createTask(alice.id(), projectId, todo.getId(), "Design API", "", TaskPriority.HIGH, null);
        UUID second = projects.createTask(alice.id(), projectId, todo.getId(), "Build API", "", TaskPriority.HIGH, null);
        estimate(projectId, first, 3); estimate(projectId, second, 4);
        schedules.addDependency(alice.id(), projectId, first, second, TaskDependencyType.FS, 1);

        ProjectSchedulePage scheduled = schedules.page(alice.id(), projectId, null, null, null);
        assertEquals("READY", scheduled.schedule().status().name());
        assertEquals(8, scheduled.schedule().projectDurationDays());
        assertEquals(start.plusDays(4), scheduled.schedule().taskResults().get(second).earliestStart());
        assertEquals(1, scheduled.dependencies().size());
        assertEquals(2, scheduled.graphNodes().size());

        UUID baselineId = schedules.createBaseline(alice.id(), projectId, "Approved plan");
        String immutableSnapshot = baselines.findById(baselineId).orElseThrow().getSnapshotJson();
        Task firstTask = tasks.findDetailByIdAndOwner(first, alice.id()).orElseThrow();
        long version = projectRepository.findByIdAndOwner_Id(projectId, alice.id()).orElseThrow().getVersion();
        ScheduleChangePreview preview = schedules.preview(alice.id(), projectId, first, 5, null,
                version, firstTask.getVersion());
        assertTrue(preview.canConfirm());
        assertEquals(10, preview.proposed().projectDurationDays());
        assertTrue(preview.impacts().stream().anyMatch(impact -> impact.title().equals("Build API")
                && impact.proposedStart().equals(start.plusDays(6))));
        mvc.perform(post("/projects/{id}/scheduling/tasks/{taskId}/preview", projectId, first).with(user(alice.principal())).with(csrf())
                        .param("durationDays", "5").param("projectVersion", Long.toString(version))
                        .param("taskVersion", Long.toString(firstTask.getVersion())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("SCHEDULE IMPACT PREVIEW")));
        schedules.apply(alice.id(), projectId, first, 5, null, version, firstTask.getVersion());

        ProjectSchedulePage compared = schedules.page(alice.id(), projectId, baselineId, null, null);
        assertEquals(immutableSnapshot, baselines.findById(baselineId).orElseThrow().getSnapshotJson());
        assertTrue(compared.baselineComparison().stream().anyMatch(impact -> impact.title().equals("Build API")
                && impact.proposedStart().equals(start.plusDays(6))));
        mvc.perform(get("/projects/{id}/gantt", projectId).with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Timeline and critical path")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Build API")));
        mvc.perform(get("/projects/{id}/dependencies", projectId).with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("dependency-graph")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Design API")));
        mvc.perform(get("/tasks/{id}", first).with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Open project timeline")));
        mvc.perform(get("/projects/{id}/gantt", projectId).with(user(bob.principal()))).andExpect(status().isNotFound());
        mvc.perform(post("/projects/{id}/scheduling/dependencies", projectId).with(user(alice.principal())))
                .andExpect(status().isForbidden());
    }

    @Test
    @org.springframework.transaction.annotation.Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void projectLockPreventsConcurrentDependencyCycleCreation() throws Exception {
        LocalDate start = LocalDate.of(2026, 1, 5);
        ProjectForm form = projectForm("Concurrent schedule"); form.setStartDate(start);
        UUID projectId = projects.create(alice.id(), form, null);
        BoardColumn todo = column(projectId, BoardStage.TODO);
        UUID first = projects.createTask(alice.id(), projectId, todo.getId(), "Concurrent A", "", TaskPriority.MEDIUM, null);
        UUID second = projects.createTask(alice.id(), projectId, todo.getId(), "Concurrent B", "", TaskPriority.MEDIUM, null);
        CountDownLatch ready = new CountDownLatch(2), startGate = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var forward = workers.submit(() -> concurrentDependency(ready, startGate, projectId, first, second));
            var reverse = workers.submit(() -> concurrentDependency(ready, startGate, projectId, second, first));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); startGate.countDown();
            assertEquals(1, forward.get(10, TimeUnit.SECONDS) + reverse.get(10, TimeUnit.SECONDS));
            assertEquals(1, dependencies.findAllByOwnerIdAndProjectIdOrderByCreatedAtAsc(alice.id(), projectId).size());
        } finally {
            workers.shutdownNow();
            deleteConcurrentTestUsers();
        }
    }

    private int concurrentDependency(CountDownLatch ready, CountDownLatch startGate, UUID projectId, UUID predecessor, UUID successor)
            throws InterruptedException {
        ready.countDown(); startGate.await();
        try { schedules.addDependency(alice.id(), projectId, predecessor, successor, TaskDependencyType.FS, 0); return 1; }
        catch (TaskRuleException exception) { return 0; }
    }

    private void deleteConcurrentTestUsers() {
        jdbc.update("delete from task_os.project_task_placements where owner_id in (?, ?)", alice.id(), bob.id());
        jdbc.update("delete from task_os.users where id in (?, ?)", alice.id(), bob.id());
    }

    private void estimate(UUID projectId, UUID taskId, int days) {
        Task task = tasks.findDetailByIdAndOwner(taskId, alice.id()).orElseThrow();
        long projectVersion = projectRepository.findByIdAndOwner_Id(projectId, alice.id()).orElseThrow().getVersion();
        schedules.apply(alice.id(), projectId, taskId, days, null, projectVersion, task.getVersion());
    }

    private UUID createProject(TestUser user, String name) { return projects.create(user.id(), projectForm(name), null); }
    private ProjectForm projectForm(String name) {
        ProjectForm form = new ProjectForm(); form.setName(name); form.setStatus(ProjectStatus.ACTIVE); form.setColor("#315fe8"); return form;
    }
    private BoardColumn column(UUID projectId, BoardStage stage) {
        return projects.columns(alice.id(), projectId).stream().filter(item -> item.getStage() == stage).findFirst().orElseThrow();
    }
    private UUID task(String title, TestUser user) {
        com.taskos.task.dto.TaskForm form = new com.taskos.task.dto.TaskForm();
        form.setTitle(title); form.setStatus(TaskStatus.TODO); form.setPriority(TaskPriority.MEDIUM);
        return taskService.create(user.id(), form);
    }
    private TestUser createUser(String name) {
        var account = registration.register(name, name.toLowerCase() + "-" + UUID.randomUUID() + "@example.test", PASSWORD);
        return new TestUser(account.getId(), userDetails.loadUserByUsername(account.getEmail()));
    }
    private record TestUser(UUID id, UserDetails principal) { }
}
