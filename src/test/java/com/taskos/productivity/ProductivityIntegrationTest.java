package com.taskos.productivity;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskos.auth.service.RegistrationService;
import com.taskos.productivity.entity.*;
import com.taskos.productivity.repository.DailyPlanEntryRepository;
import com.taskos.productivity.repository.GtdInboxRepository;
import com.taskos.productivity.repository.TaskProductivityRepository;
import com.taskos.productivity.service.ProductivityService;
import com.taskos.security.service.DatabaseUserDetailsService;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskActivityType;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.repository.TaskActivityRepository;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskService;
import com.taskos.task.service.TaskRuleException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
class ProductivityIntegrationTest {
    private static final String PASSWORD = "a sufficiently long test passphrase";
    @Autowired private MockMvc mvc;
    @Autowired private RegistrationService registration;
    @Autowired private DatabaseUserDetailsService userDetails;
    @Autowired private TaskService taskService;
    @Autowired private ProductivityService productivity;
    @Autowired private TaskRepository tasks;
    @Autowired private TaskProductivityRepository metadata;
    @Autowired private GtdInboxRepository inbox;
    @Autowired private DailyPlanEntryRepository planEntries;
    @Autowired private TaskActivityRepository activity;
    @Autowired private JdbcTemplate jdbc;

    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void createUsers() {
        alice = createUser("Alice");
        bob = createUser("Bob");
    }

    @Test
    void productivityPagesRenderAndRoutesRequireAuthentication() throws Exception {
        createTask(alice, "A real task");
        mvc.perform(get("/productivity").with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Choose how you work")));
        for (String route : List.of("/productivity/eisenhower", "/productivity/gtd", "/productivity/abc",
                "/productivity/moscow", "/productivity/pareto", "/productivity/daily", "/productivity/weekly-review")) {
            mvc.perform(get(route).with(user(alice.principal()))).andExpect(status().isOk());
        }
        mvc.perform(get("/productivity")).andExpect(status().is3xxRedirection());
        mvc.perform(post("/productivity/gtd/capture").with(user(alice.principal())).param("text", "No csrf"))
                .andExpect(status().isForbidden());
    }

    @Test
    void classificationsAreOwnerScopedIndependentAndDoNotOverwriteTaskPriority() throws Exception {
        UUID taskId = createTask(alice, "Prepare proposal", TaskPriority.HIGH);
        mvc.perform(post("/productivity/eisenhower/{id}", taskId).with(user(alice.principal())).with(csrf())
                        .param("quadrant", "DELEGATE"))
                .andExpect(status().is3xxRedirection());
        TaskProductivity item = metadata.findByIdAndOwner_Id(taskId, alice.id()).orElseThrow();
        assertTrue(item.getUrgent());
        assertFalse(item.getImportant());
        assertEquals(TaskPriority.HIGH, tasks.findById(taskId).orElseThrow().getPriority());

        mvc.perform(post("/productivity/abc/{id}", taskId).with(user(alice.principal())).with(csrf())
                        .param("category", "A"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/productivity/moscow/{id}", taskId).with(user(alice.principal())).with(csrf())
                        .param("category", "MUST_HAVE"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/productivity/pareto/{id}", taskId).with(user(alice.principal())).with(csrf())
                        .param("impact", "5").param("effort", "2").param("note", "User estimate"))
                .andExpect(status().is3xxRedirection());
        assertEquals(AbcCategory.A, metadata.findByIdAndOwner_Id(taskId, alice.id()).orElseThrow().getAbcCategory());
        assertEquals(MoscowCategory.MUST_HAVE, metadata.findByIdAndOwner_Id(taskId, alice.id()).orElseThrow().getMoscowCategory());
        assertEquals(5, metadata.findByIdAndOwner_Id(taskId, alice.id()).orElseThrow().getImpactScore());
        assertTrue(activity.findAllByOwner_IdAndTaskIdOrderByOccurredAtDesc(alice.id(), taskId,
                org.springframework.data.domain.PageRequest.of(0, 30)).stream()
                .anyMatch(event -> event.getEventType() == TaskActivityType.EISENHOWER_CLASSIFIED));

        mvc.perform(post("/productivity/eisenhower/{id}", taskId).with(user(bob.principal())).with(csrf())
                        .param("quadrant", "DO"))
                .andExpect(status().isNotFound());
        assertTrue(metadata.findByIdAndOwner_Id(taskId, bob.id()).isEmpty());
    }

    @Test
    void inboxConversionCreatesOneCanonicalTaskAndRejectsSecondConversion() throws Exception {
        mvc.perform(post("/productivity/gtd/capture").with(user(alice.principal())).with(csrf())
                        .param("text", "Send the project brief"))
                .andExpect(status().is3xxRedirection());
        GtdInboxItem captured = productivity.inbox(alice.id(), InboxState.OPEN).getFirst();
        long before = tasks.countByOwner_Id(alice.id());
        mvc.perform(post("/productivity/gtd/inbox/{id}", captured.getId()).with(user(alice.principal())).with(csrf())
                        .param("outcome", "CONVERTED").param("priority", "MEDIUM")
                        .param("taskTitle", "Send the project brief"))
                .andExpect(status().is3xxRedirection());
        assertEquals(before + 1, tasks.countByOwner_Id(alice.id()));
        assertEquals(InboxState.CONVERTED, inbox.findByIdAndOwner_Id(captured.getId(), alice.id()).orElseThrow().getState());
        assertEquals(1, jdbc.queryForObject("select count(*) from task_os.tasks where owner_id = ? and title = ?",
                Integer.class, alice.id(), "Send the project brief"));
        mvc.perform(post("/productivity/gtd/inbox/{id}", captured.getId()).with(user(alice.principal())).with(csrf())
                        .param("outcome", "CONVERTED"))
                .andExpect(status().is3xxRedirection());
        assertEquals(before + 1, tasks.countByOwner_Id(alice.id()));
    }

    @Test
    void somedayAndReferenceCapturesRenderAndCanReturnToInbox() throws Exception {
        for (InboxState state : List.of(InboxState.SOMEDAY, InboxState.REFERENCE)) {
            mvc.perform(post("/productivity/gtd/capture").with(user(alice.principal())).with(csrf())
                            .param("text", "Capture " + state))
                    .andExpect(status().is3xxRedirection());
            GtdInboxItem item = productivity.inbox(alice.id(), InboxState.OPEN).stream()
                    .filter(capture -> capture.getCaptureText().equals("Capture " + state)).findFirst().orElseThrow();
            mvc.perform(post("/productivity/gtd/inbox/{id}", item.getId()).with(user(alice.principal())).with(csrf())
                            .param("outcome", state.name()))
                    .andExpect(status().is3xxRedirection());
            mvc.perform(get("/productivity/gtd").param("list", state.name()).with(user(alice.principal())))
                    .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Capture " + state)));
            mvc.perform(post("/productivity/gtd/inbox/{id}/reopen", item.getId()).with(user(alice.principal())).with(csrf()))
                    .andExpect(status().is3xxRedirection());
            assertEquals(InboxState.OPEN, inbox.findByIdAndOwner_Id(item.getId(), alice.id()).orElseThrow().getState());
        }
    }

    @Test
    void creatingAPlannedTaskUsesTheCanonicalTaskRecord() throws Exception {
        LocalDate date = productivity.today(alice.id());
        mvc.perform(post("/productivity/daily/create").with(user(alice.principal())).with(csrf())
                        .param("date", date.toString()).param("title", "Plan a launch")
                        .param("bucket", "IVY_LEE"))
                .andExpect(status().is3xxRedirection());
        assertEquals(1, tasks.countByOwner_Id(alice.id()));
        assertEquals("Plan a launch", productivity.planEntries(alice.id(), date).getFirst().getTask().getTitle());
    }

    @Test
    void dailyPlanEnforcesCapacityAndRequiresExplicitModeConversion() {
        LocalDate date = productivity.today(alice.id());
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 7; i++) ids.add(createTask(alice, "Plan item " + i));
        for (int i = 0; i < 6; i++) productivity.addToPlan(alice.id(), date, ids.get(i), PlanBucket.IVY_LEE);
        assertEquals(6, planEntries.findAllByPlan_IdOrderByPositionAsc(
                productivity.dailyPlan(alice.id(), date).getId()).size());
        assertThrows(TaskRuleException.class, () -> productivity.addToPlan(alice.id(), date, ids.get(6), PlanBucket.IVY_LEE));
        assertThrows(TaskRuleException.class, () -> productivity.switchPlanMode(alice.id(), date,
                DailyPlanMode.ONE_THREE_FIVE, false, null));
        List<PlanBucket> sizes = List.of(PlanBucket.BIG, PlanBucket.MEDIUM, PlanBucket.MEDIUM,
                PlanBucket.MEDIUM, PlanBucket.SMALL, PlanBucket.SMALL);
        productivity.switchPlanMode(alice.id(), date, DailyPlanMode.ONE_THREE_FIVE, true, sizes);
        assertEquals(DailyPlanMode.ONE_THREE_FIVE, productivity.dailyPlan(alice.id(), date).getMode());
        assertThrows(TaskRuleException.class, () -> productivity.addToPlan(alice.id(), date, ids.get(6), PlanBucket.BIG));
        assertThrows(TaskRuleException.class, () -> productivity.addToPlan(alice.id(), date, ids.get(0), PlanBucket.SMALL));
    }

    @Test
    void weeklyReviewOnlyCompletesWhenEverySectionIsChecked() {
        LocalDate week = productivity.today(alice.id()).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        productivity.saveWeeklyReview(alice.id(), week, true, true, true, true, false);
        assertNull(productivity.weeklyReview(alice.id(), week).getCompletedAt());
        productivity.saveWeeklyReview(alice.id(), week, true, true, true, true, true);
        assertNotNull(productivity.weeklyReview(alice.id(), week).getCompletedAt());
    }

    private TestUser createUser(String name) {
        var account = registration.register(name, name.toLowerCase() + "-" + UUID.randomUUID() + "@example.test", PASSWORD);
        return new TestUser(account.getId(), userDetails.loadUserByUsername(account.getEmail()));
    }

    private UUID createTask(TestUser user, String title) { return createTask(user, title, TaskPriority.MEDIUM); }

    private UUID createTask(TestUser user, String title, TaskPriority priority) {
        TaskForm form = new TaskForm(); form.setTitle(title); form.setStatus(TaskStatus.TODO); form.setPriority(priority);
        return taskService.create(user.id(), form);
    }

    private record TestUser(UUID id, UserDetails principal) { }
}
