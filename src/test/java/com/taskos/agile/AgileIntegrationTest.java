package com.taskos.agile;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskos.agile.entity.AgileWorkItemType;
import com.taskos.agile.entity.SprintEventType;
import com.taskos.agile.entity.SprintMembershipStatus;
import com.taskos.agile.entity.SprintStatus;
import com.taskos.agile.repository.AgileWorkItemRepository;
import com.taskos.agile.repository.SprintEventRepository;
import com.taskos.agile.repository.SprintMembershipRepository;
import com.taskos.agile.repository.SprintRepository;
import com.taskos.agile.service.AgileService;
import com.taskos.auth.service.RegistrationService;
import com.taskos.project.dto.ProjectForm;
import com.taskos.project.entity.BoardColumn;
import com.taskos.project.entity.BoardStage;
import com.taskos.project.entity.Project;
import com.taskos.project.entity.ProjectStatus;
import com.taskos.project.service.ProjectService;
import com.taskos.security.service.DatabaseUserDetailsService;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskRuleException;
import com.taskos.task.service.TaskService;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AgileIntegrationTest {
    private static final String PASSWORD = "a sufficiently long test passphrase";
    @Autowired private MockMvc mvc;
    @Autowired private RegistrationService registration;
    @Autowired private DatabaseUserDetailsService userDetails;
    @Autowired private ProjectService projects;
    @Autowired private AgileService agile;
    @Autowired private TaskService taskService;
    @Autowired private TaskRepository tasks;
    @Autowired private AgileWorkItemRepository workItems;
    @Autowired private SprintRepository sprints;
    @Autowired private SprintMembershipRepository memberships;
    @Autowired private SprintEventRepository events;
    @Autowired private JdbcTemplate jdbc;

    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void createUsers() { alice = createUser("Alice"); bob = createUser("Bob"); }

    @Test
    void scrumPagesRenderAndStoriesEpicsCriteriaAndCeremoniesPersist() throws Exception {
        UUID projectId = createProject(alice, "Scrum delivery");
        UUID taskId = createProjectTask(alice, projectId, "Search tasks", "Find work by title");
        agile.configure(alice.id(), projectId, true, "Implementation complete\nTests reviewed");
        var epic = agile.createEpic(alice.id(), projectId, "Task discovery", "Find important work", null);
        agile.updateWorkItem(alice.id(), projectId, taskId, AgileWorkItemType.STORY, 3, epic.getId(), "Review acceptance evidence");
        var criterion = agile.addCriterion(alice.id(), projectId, taskId, "Given a query, matching tasks appear.");
        agile.satisfyCriterion(alice.id(), projectId, taskId, criterion.getId(), true);
        assertEquals(1, tasks.countByOwner_Id(alice.id()));
        assertEquals(1, agile.backlogItems(alice.id(), projectId).size());
        var definitionOfDone = agile.backlogItems(alice.id(), projectId).getFirst().definitionOfDone();
        assertEquals(2, definitionOfDone.size());
        agile.satisfyDefinitionOfDoneCriterion(alice.id(), projectId, taskId, definitionOfDone.getFirst().getId(), true);
        assertTrue(agile.backlogItems(alice.id(), projectId).getFirst().definitionOfDone().getFirst().isSatisfied());
        assertEquals(1, agile.epicProgress(alice.id(), projectId).getFirst().totalItems());

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var sprint = agile.createSprint(alice.id(), projectId, "Sprint one", "Make search discoverable", "", today, today.plusDays(13), 8);
        agile.addToSprint(alice.id(), projectId, sprint.getId(), taskId, null);
        String root = "/projects/" + projectId;
        for (String suffix : List.of("/agile", "/backlog", "/epics", "/sprints", "/sprints/new",
                "/sprints/" + sprint.getId() + "/planning"))
            mvc.perform(get(root + suffix).with(user(alice.principal()))).andExpect(status().isOk());

        agile.startSprint(alice.id(), projectId, sprint.getId());
        mvc.perform(get(root + "/sprints/active").with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Actual remaining work")));
        mvc.perform(get(root + "/sprints/" + sprint.getId() + "/daily").with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Add a check-in")));
        mvc.perform(get(root + "/sprints/" + sprint.getId() + "/review").with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Accepted items")));
        taskService.transition(alice.id(), taskId, TaskStatus.COMPLETED);
        agile.completeSprint(alice.id(), projectId, sprint.getId(), "BACKLOG", null);
        agile.saveReview(alice.id(), projectId, sprint.getId(), "Goal delivered", "Search shipped", "", "Demo notes", "Feedback", "Search accepted by user", "", "Follow up");
        agile.saveRetrospective(alice.id(), projectId, sprint.getId(), "Clear scope", "Start testing earlier", "Review search logs", "Add query analytics");
        var actionItem = agile.addRetrospectiveAction(alice.id(), projectId, sprint.getId(), "Add query analytics");
        UUID actionTaskId = agile.convertRetrospectiveAction(alice.id(), projectId, sprint.getId(), actionItem.getId());
        assertEquals(actionTaskId, agile.convertRetrospectiveAction(alice.id(), projectId, sprint.getId(), actionItem.getId()));
        assertEquals("Add query analytics", tasks.findById(actionTaskId).orElseThrow().getTitle());
        mvc.perform(get(root + "/sprints/" + sprint.getId()).with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("COMPLETED SPRINT SUMMARY")));
        mvc.perform(get(root + "/sprints/" + sprint.getId() + "/retrospective").with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Clear scope")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Add query analytics")));
        mvc.perform(get(root + "/sprints/history").with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("3 points delivered")));
        assertEquals(3, sprints.findById(sprint.getId()).orElseThrow().getDeliveredPoints());
    }

    @Test
    void canonicalTaskStatusDrivesBurndownAndCompletedVelocityStaysImmutable() {
        UUID projectId = createProject(alice, "Canonical sprint work");
        UUID taskId = createProjectTask(alice, projectId, "A story", "Original task record");
        agile.configure(alice.id(), projectId, true, "Review complete");
        agile.updateWorkItem(alice.id(), projectId, taskId, AgileWorkItemType.STORY, 5, null, "Acceptance evidence checked");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var sprint = agile.createSprint(alice.id(), projectId, "Sprint metrics", "Finish the story", "", today, today.plusDays(7), null);
        agile.addToSprint(alice.id(), projectId, sprint.getId(), taskId, null);
        agile.startSprint(alice.id(), projectId, sprint.getId());
        assertEquals(1, agile.metrics(alice.id(), projectId, sprint.getId()).remainingItems());
        taskService.transition(alice.id(), taskId, TaskStatus.IN_PROGRESS);
        taskService.transition(alice.id(), taskId, TaskStatus.COMPLETED);
        assertTrue(events.findAllBySprintIdOrderByOccurredAtAsc(sprint.getId()).stream()
                .anyMatch(event -> event.getEventType() == SprintEventType.TASK_COMPLETED));
        assertEquals(0, agile.burndown(alice.id(), projectId, sprint.getId()).getFirst().actualRemainingPoints());
        agile.completeSprint(alice.id(), projectId, sprint.getId(), "BACKLOG", null);
        taskService.transition(alice.id(), taskId, TaskStatus.TODO);
        assertEquals(5, sprints.findById(sprint.getId()).orElseThrow().getDeliveredPoints());
        assertEquals(5.0, agile.averageVelocity(alice.id(), projectId));
        assertEquals(1, tasks.findAllByOwner_IdAndIdIn(alice.id(), List.of(taskId)).size());
        assertEquals(0, agile.burndown(alice.id(), projectId, sprint.getId()).getFirst().actualRemainingPoints());
    }

    @Test
    void activeScopeChangesAreAuditedAndCarryoverDoesNotEarnVelocityTwice() {
        UUID projectId = createProject(alice, "Carryover policy");
        UUID deliveredTask = createProjectTask(alice, projectId, "Delivered story", "");
        UUID unfinishedTask = createProjectTask(alice, projectId, "Unfinished story", "");
        agile.configure(alice.id(), projectId, true, "");
        agile.updateWorkItem(alice.id(), projectId, deliveredTask, AgileWorkItemType.STORY, 3, null, "");
        agile.updateWorkItem(alice.id(), projectId, unfinishedTask, AgileWorkItemType.STORY, 5, null, "");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var current = agile.createSprint(alice.id(), projectId, "Current", "Deliver one", "", today, today.plusDays(13), null);
        var next = agile.createSprint(alice.id(), projectId, "Next", "Continue safely", "", today.plusDays(14), today.plusDays(27), 13);
        agile.addToSprint(alice.id(), projectId, current.getId(), deliveredTask, null);
        agile.startSprint(alice.id(), projectId, current.getId());
        agile.addToSprint(alice.id(), projectId, current.getId(), unfinishedTask, "Urgent product need");
        agile.updateWorkItem(alice.id(), projectId, unfinishedTask, AgileWorkItemType.STORY, 8, null, "");
        agile.removeFromSprint(alice.id(), projectId, current.getId(), unfinishedTask, "Split out remaining scope");
        agile.addToSprint(alice.id(), projectId, current.getId(), unfinishedTask, "Keep the dependency together");
        taskService.transition(alice.id(), deliveredTask, TaskStatus.COMPLETED);
        agile.completeSprint(alice.id(), projectId, current.getId(), "PLANNED_SPRINT", next.getId());

        assertEquals(3, sprints.findById(current.getId()).orElseThrow().getDeliveredPoints());
        assertEquals(SprintMembershipStatus.INCOMPLETE,
                memberships.findBySprintIdAndTaskId(current.getId(), unfinishedTask).orElseThrow().getStatus());
        assertEquals(SprintMembershipStatus.PLANNED,
                memberships.findBySprintIdAndTaskId(next.getId(), unfinishedTask).orElseThrow().getStatus());
        assertEquals(4, agile.metrics(alice.id(), projectId, current.getId()).scopeChanges(),
                events.findAllBySprintIdOrderByOccurredAtAsc(current.getId()).stream()
                        .map(event -> event.getEventType()).toList().toString());
        assertEquals(3.0, agile.averageVelocity(alice.id(), projectId));
    }

    @Test
    void projectOwnershipCsrfAndWorkItemAssociationAreEnforced() throws Exception {
        UUID projectId = createProject(alice, "Private Scrum");
        UUID taskId = createProjectTask(alice, projectId, "Private story", "");
        agile.configure(alice.id(), projectId, true, "");
        UUID otherProject = createProject(alice, "Other Scrum");
        agile.configure(alice.id(), otherProject, true, "");
        assertThrows(RuntimeException.class, () -> agile.updateWorkItem(alice.id(), otherProject, taskId,
                AgileWorkItemType.BUG, 3, null, ""));
        mvc.perform(get("/projects/" + projectId + "/backlog").with(user(bob.principal())))
                .andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + projectId + "/backlog")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/projects/" + projectId + "/backlog/" + taskId + "/criteria")
                        .with(user(alice.principal())).param("criterion", "No token"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/projects/" + projectId + "/backlog").with(user(alice.principal())))
                .andExpect(status().isOk());
        mvc.perform(post("/projects/" + projectId + "/backlog/" + taskId + "/criteria")
                        .with(user(alice.principal())).with(csrf()).param("criterion", "Authorized criterion"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    void twoConcurrentStartRequestsCannotCreateTwoActiveSprints() throws Exception {
        UUID projectId = createProject(alice, "Concurrent start");
        UUID firstTask = createProjectTask(alice, projectId, "First", "");
        UUID secondTask = createProjectTask(alice, projectId, "Second", "");
        agile.configure(alice.id(), projectId, true, "");
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        var first = agile.createSprint(alice.id(), projectId, "First sprint", "First goal", "", today, today.plusDays(13), null);
        var second = agile.createSprint(alice.id(), projectId, "Second sprint", "Second goal", "", today, today.plusDays(13), null);
        agile.addToSprint(alice.id(), projectId, first.getId(), firstTask, null);
        agile.addToSprint(alice.id(), projectId, second.getId(), secondTask, null);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            var one = workers.submit(() -> concurrentStart(ready, start, projectId, first.getId()));
            var two = workers.submit(() -> concurrentStart(ready, start, projectId, second.getId()));
            assertTrue(ready.await(5, TimeUnit.SECONDS)); start.countDown();
            assertEquals(1, one.get(10, TimeUnit.SECONDS) + two.get(10, TimeUnit.SECONDS));
            assertEquals(1, agile.sprints(alice.id(), projectId).stream().filter(item -> item.getStatus() == SprintStatus.ACTIVE).count());
        } finally {
            workers.shutdownNow(); cleanupOwner(alice.id()); cleanupOwner(bob.id());
        }
    }

    private int concurrentStart(CountDownLatch ready, CountDownLatch start, UUID projectId, UUID sprintId) throws InterruptedException {
        ready.countDown(); start.await();
        try { agile.startSprint(alice.id(), projectId, sprintId); return 1; }
        catch (TaskRuleException ex) { return 0; }
    }

    private void cleanupOwner(UUID ownerId) {
        jdbc.update("delete from task_os.sprint_impediments where sprint_id in (select id from task_os.sprints where project_id in (select id from task_os.projects where owner_id = ?))", ownerId);
        jdbc.update("delete from task_os.sprint_daily_notes where sprint_id in (select id from task_os.sprints where project_id in (select id from task_os.projects where owner_id = ?))", ownerId);
        jdbc.update("delete from task_os.sprint_events where project_id in (select id from task_os.projects where owner_id = ?)", ownerId);
        jdbc.update("delete from task_os.sprint_baseline_items where sprint_id in (select id from task_os.sprints where project_id in (select id from task_os.projects where owner_id = ?))", ownerId);
        jdbc.update("delete from task_os.sprint_memberships where sprint_id in (select id from task_os.sprints where project_id in (select id from task_os.projects where owner_id = ?))", ownerId);
        jdbc.update("delete from task_os.sprint_ceremonies where sprint_id in (select id from task_os.sprints where project_id in (select id from task_os.projects where owner_id = ?))", ownerId);
        jdbc.update("delete from task_os.sprint_action_items where sprint_id in (select id from task_os.sprints where project_id in (select id from task_os.projects where owner_id = ?))", ownerId);
        jdbc.update("delete from task_os.sprints where project_id in (select id from task_os.projects where owner_id = ?)", ownerId);
        jdbc.update("delete from task_os.acceptance_criteria where task_id in (select task_id from task_os.agile_work_items where project_id in (select id from task_os.projects where owner_id = ?))", ownerId);
        jdbc.update("delete from task_os.agile_work_items where project_id in (select id from task_os.projects where owner_id = ?)", ownerId);
        jdbc.update("delete from task_os.epics where project_id in (select id from task_os.projects where owner_id = ?)", ownerId);
        jdbc.update("delete from task_os.agile_project_settings where project_id in (select id from task_os.projects where owner_id = ?)", ownerId);
        jdbc.update("delete from task_os.project_task_placements where project_id in (select id from task_os.projects where owner_id = ?)", ownerId);
        jdbc.update("delete from task_os.project_activity where owner_id = ?", ownerId);
        jdbc.update("delete from task_os.project_milestones where project_id in (select id from task_os.projects where owner_id = ?)", ownerId);
        jdbc.update("delete from task_os.project_board_columns where project_id in (select id from task_os.projects where owner_id = ?)", ownerId);
        jdbc.update("delete from task_os.projects where owner_id = ?", ownerId);
        jdbc.update("delete from task_os.users where id = ?", ownerId);
    }

    private UUID createProject(TestUser user, String name) {
        ProjectForm form = new ProjectForm(); form.setName(name); form.setStatus(ProjectStatus.ACTIVE); form.setColor("#315fe8");
        return projects.create(user.id(), form, null);
    }

    private UUID createProjectTask(TestUser user, UUID projectId, String title, String description) {
        BoardColumn todo = projects.columns(user.id(), projectId).stream().filter(column -> column.getStage() == BoardStage.TODO).findFirst().orElseThrow();
        return projects.createTask(user.id(), projectId, todo.getId(), title, description, TaskPriority.MEDIUM, null);
    }

    private TestUser createUser(String name) {
        var account = registration.register(name, name.toLowerCase() + "-" + UUID.randomUUID() + "@example.test", PASSWORD);
        return new TestUser(account.getId(), userDetails.loadUserByUsername(account.getEmail()));
    }
    private record TestUser(UUID id, UserDetails principal) { }
}
