package com.taskos.dashboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskos.auth.service.RegistrationService;
import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.dashboard.dto.DashboardOverview;
import com.taskos.dashboard.dto.DashboardTask;
import com.taskos.dashboard.service.DashboardService;
import com.taskos.security.service.DatabaseUserDetailsService;
import com.taskos.task.dto.SubtaskForm;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.entity.TaskActivityType;
import com.taskos.task.repository.TaskActivityRepository;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskService;
import com.taskos.user.entity.ThemePreference;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.service.UserProfileService;
import jakarta.persistence.EntityManager;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@Import(DashboardIntegrationTest.ClockConfiguration.class)
class DashboardIntegrationTest {
    private static final Instant DEFAULT_NOW = Instant.parse("2024-03-11T01:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2024, 3, 10);
    private static final String PASSWORD = "a sufficiently long test passphrase";

    @Autowired private MockMvc mvc;
    @Autowired private RegistrationService registration;
    @Autowired private DatabaseUserDetailsService userDetails;
    @Autowired private UserProfileService profiles;
    @Autowired private TaskService tasks;
    @Autowired private TaskRepository taskRepository;
    @Autowired private TaskActivityRepository activityRepository;
    @Autowired private DashboardService dashboard;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManager entityManager;
    @Autowired private AdjustableClock testClock;

    private TestUser alice;
    private TestUser bob;

    @BeforeEach
    void createUsersInLosAngelesTimezone() {
        testClock.set(DEFAULT_NOW);
        alice = createUser("Alice", "America/Los_Angeles");
        bob = createUser("Bob", "UTC");
    }

    @Test
    void metricsUseAccountLocalDateAndExcludeClosedArchivedAndChildTasks() {
        UUID overdue = createTask(alice, "Past deadline", TODAY.minusDays(1), TaskPriority.LOW, TaskStatus.TODO);
        createTask(alice, "Due today", TODAY, TaskPriority.MEDIUM, TaskStatus.IN_PROGRESS);
        UUID parent = createTask(alice, "Parent due today", TODAY, TaskPriority.MEDIUM, TaskStatus.TODO);
        SubtaskForm childForm = new SubtaskForm();
        childForm.setTitle("Child is not counted separately");
        tasks.createSubtask(alice.account().getId(), parent, childForm);
        createTask(alice, "Due tomorrow", TODAY.plusDays(1), TaskPriority.LOW, TaskStatus.TODO);
        createTask(alice, "Due in seven days", TODAY.plusDays(7), TaskPriority.LOW, TaskStatus.TODO);
        createTask(alice, "Due in eight days", TODAY.plusDays(8), TaskPriority.LOW, TaskStatus.TODO);
        createTask(alice, "Unscheduled work", null, TaskPriority.MEDIUM, TaskStatus.ON_HOLD);
        createTask(alice, "Urgent later work", TODAY.plusDays(15), TaskPriority.URGENT, TaskStatus.TODO);

        UUID completedToday = createTask(alice, "Completed during the DST day", TODAY,
                TaskPriority.MEDIUM, TaskStatus.COMPLETED);
        setCompletedAt(completedToday, Instant.parse("2024-03-11T06:59:59Z"));
        UUID completedEarlier = createTask(alice, "Completed at start of reporting week", TODAY,
                TaskPriority.MEDIUM, TaskStatus.COMPLETED);
        setCompletedAt(completedEarlier, Instant.parse("2024-03-04T08:10:00Z"));
        UUID completedNextDay = createTask(alice, "Completed next local day", TODAY,
                TaskPriority.MEDIUM, TaskStatus.COMPLETED);
        setCompletedAt(completedNextDay, Instant.parse("2024-03-11T07:00:00Z"));
        createTask(alice, "Cancelled due today", TODAY, TaskPriority.URGENT, TaskStatus.CANCELLED);
        UUID archived = createTask(alice, "Archived due today", TODAY, TaskPriority.URGENT, TaskStatus.TODO);
        tasks.archive(alice.account().getId(), archived);
        createTask(bob, "Bob private overdue", TODAY.minusDays(4), TaskPriority.URGENT, TaskStatus.TODO);

        DashboardOverview data = dashboard.overview(alice.account().getId());

        assertEquals(TODAY, data.today(), "UTC has rolled over, but Los Angeles is still on March 10");
        assertEquals(8, data.metrics().activeTaskCount(), "Dashboard counts top-level actionable work only");
        assertEquals(2, data.metrics().dueTodayCount());
        assertEquals(1, data.metrics().dueTomorrowCount());
        assertEquals(2, data.metrics().dueNextSevenDaysCount(), "Tomorrow and day seven are both inside the window");
        assertEquals(1, data.metrics().overdueCount());
        assertEquals(1, data.metrics().unscheduledCount());
        assertEquals(1, data.metrics().completedTodayCount(), "The 23-hour DST day ends at 07:00Z");
        assertEquals(2, data.metrics().completedLastSevenDaysCount(), "The exact next-local-day boundary is excluded");
        assertTrue(data.myDay().stream().anyMatch(task -> task.id().equals(overdue)));
        assertFalse(data.myDay().stream().anyMatch(task -> task.id().equals(completedToday)));
        assertTrue(data.completionTrend().stream().anyMatch(point -> point.date().equals(TODAY)
                && point.taskCount() == 1));
        assertTrue(data.completionTrend().stream().anyMatch(point -> point.date().equals(TODAY.minusDays(6))
                && point.taskCount() == 1));

        DashboardOverview bobData = dashboard.overview(bob.account().getId());
        assertEquals(1, bobData.metrics().activeTaskCount());
        assertEquals(0, bobData.metrics().completedTodayCount());
    }

    @Test
    void myDayOrderingIsDeterministicAndDoesNotDuplicateCrossCategoryTasks() {
        createTask(alice, "Overdue low", TODAY.minusDays(2), TaskPriority.LOW, TaskStatus.TODO);
        createTask(alice, "Today low", TODAY, TaskPriority.LOW, TaskStatus.TODO);
        createTask(alice, "Today urgent", TODAY, TaskPriority.URGENT, TaskStatus.TODO);
        createTask(alice, "Urgent later", TODAY.plusDays(4), TaskPriority.URGENT, TaskStatus.TODO);
        createTask(alice, "High later", TODAY.plusDays(3), TaskPriority.HIGH, TaskStatus.TODO);
        createTask(alice, "Low later excluded", TODAY.plusDays(5), TaskPriority.LOW, TaskStatus.TODO);

        List<DashboardTask> ordered = dashboard.myDay(alice.account().getId(), 0).tasks();

        assertEquals(List.of("Overdue low", "Today urgent", "Today low", "Urgent later", "High later"),
                ordered.stream().map(DashboardTask::title).toList());
        assertEquals(ordered.size(), ordered.stream().map(DashboardTask::id).distinct().count());
        assertEquals("Overdue", ordered.getFirst().reason());
        assertEquals("Urgent priority", ordered.get(3).reason());
    }

    @Test
    void leapDayTomorrowAndDateViewsUseFixedClockAndLocalTimezone() {
        testClock.set(Instant.parse("2024-02-29T12:00:00Z"));
        createTask(alice, "Leap day", LocalDate.of(2024, 2, 29), TaskPriority.MEDIUM, TaskStatus.TODO);
        createTask(alice, "March first", LocalDate.of(2024, 3, 1), TaskPriority.HIGH, TaskStatus.TODO);

        TaskListPageAssertions.assertTitles(dashboard.today(alice.account().getId(), 0).tasks(), List.of("Leap day"));
        TaskListPageAssertions.assertTitles(dashboard.tomorrow(alice.account().getId(), 0).tasks(), List.of("March first"));
        assertEquals(LocalDate.of(2024, 2, 29), dashboard.overview(alice.account().getId()).today());
    }

    @Test
    void invalidStoredTimezoneFallsBackToUtc() {
        jdbc.update("update task_os.users set timezone = ? where id = ?", "invalid/zone", alice.account().getId());
        entityManager.clear();

        DashboardOverview overview = dashboard.overview(alice.account().getId());

        assertEquals(ZoneId.of("UTC"), overview.zone());
        assertEquals(LocalDate.of(2024, 3, 11), overview.today());
    }

    @Test
    void upcomingGroupsDoNotOverlapAndTaskViewsRender() throws Exception {
        createTask(alice, "Tomorrow group", TODAY.plusDays(1), TaskPriority.MEDIUM, TaskStatus.TODO);
        createTask(alice, "Next seven group", TODAY.plusDays(2), TaskPriority.MEDIUM, TaskStatus.TODO);
        createTask(alice, "Day seven group", TODAY.plusDays(7), TaskPriority.MEDIUM, TaskStatus.TODO);
        createTask(alice, "Later group", TODAY.plusDays(8), TaskPriority.MEDIUM, TaskStatus.TODO);
        createTask(alice, "Overdue group", TODAY.minusDays(1), TaskPriority.MEDIUM, TaskStatus.TODO);
        createTask(alice, "Completed overdue excluded", TODAY.minusDays(1), TaskPriority.MEDIUM, TaskStatus.COMPLETED);

        var upcoming = dashboard.upcoming(alice.account().getId(), 0);
        assertEquals(List.of("Tomorrow group"), upcoming.tomorrow().stream().map(DashboardTask::title).toList());
        assertEquals(List.of("Next seven group", "Day seven group"),
                upcoming.nextSevenDays().stream().map(DashboardTask::title).toList());
        assertEquals(List.of("Later group"), upcoming.later().stream().map(DashboardTask::title).toList());
        assertEquals(4, java.util.stream.Stream.of(upcoming.tomorrow(), upcoming.nextSevenDays(), upcoming.later())
                .flatMap(List::stream).map(DashboardTask::id).distinct().count());

        mvc.perform(get("/tasks/upcoming").with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Later group")));
        mvc.perform(get("/tasks/overdue").with(user(alice.principal())))
                .andExpect(status().isOk()).andExpect(content().string(org.hamcrest.Matchers.containsString("Overdue group")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Completed overdue excluded"))));
        mvc.perform(get("/tasks/today").with(user(alice.principal()))).andExpect(status().isOk());
        mvc.perform(get("/tasks/tomorrow").with(user(alice.principal()))).andExpect(status().isOk());
    }

    @Test
    void dateViewsPaginateAndKeepTheirSafeReturnRoute() throws Exception {
        for (int index = 0; index < 31; index++) {
            createTask(alice, "Paged task " + index, TODAY, TaskPriority.MEDIUM, TaskStatus.TODO);
        }

        mvc.perform(get("/my-day").with(user(alice.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/my-day?page=1\"")));
        mvc.perform(get("/my-day").param("page", "1").with(user(alice.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Page 2")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/my-day?page=0\"")));
    }

    @Test
    void quickAddAndCompleteUseAuthenticatedTaskCommandsAndCsrf() throws Exception {
        mvc.perform(post("/tasks").with(user(alice.principal())).with(csrf())
                        .param("title", "Quickly added")
                        .param("status", "TODO")
                        .param("priority", "HIGH")
                        .param("dueDate", TODAY.toString())
                        .param("returnTo", "dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
        Task saved = taskRepository.findAll().stream().filter(task -> task.getTitle().equals("Quickly added")).findFirst().orElseThrow();
        assertEquals(alice.account().getId(), saved.getOwner().getId());

        mvc.perform(post("/tasks/{id}/status", saved.getId()).with(user(alice.principal())).with(csrf())
                        .param("status", "COMPLETED").param("returnTo", "dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
        assertEquals(TaskStatus.COMPLETED, taskRepository.findById(saved.getId()).orElseThrow().getStatus());

        mvc.perform(post("/tasks/{id}/status", saved.getId()).with(user(bob.principal())).with(csrf())
                        .param("status", "COMPLETED").param("returnTo", "dashboard"))
                .andExpect(status().isNotFound());
        assertEquals(TaskStatus.COMPLETED, taskRepository.findById(saved.getId()).orElseThrow().getStatus());
        mvc.perform(post("/tasks/{id}/status", saved.getId()).with(user(alice.principal())).with(csrf())
                        .param("status", "TODO").param("returnTo", "dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
        assertEquals(TaskStatus.TODO, taskRepository.findById(saved.getId()).orElseThrow().getStatus());
        assertTrue(activityRepository.findAllByOwner_IdAndTaskIdOrderByOccurredAtDesc(
                alice.account().getId(), saved.getId(), org.springframework.data.domain.PageRequest.of(0, 10))
                .stream().anyMatch(event -> event.getEventType() == TaskActivityType.TASK_REOPENED));
        mvc.perform(post("/tasks/{id}/status", saved.getId()).with(user(alice.principal()))
                        .param("status", "TODO"))
                .andExpect(status().isForbidden());
    }

    @Test
    void privatePagesRequireAuthenticationAndEachOwnerSeesOnlyTheirTasks() throws Exception {
        createTask(alice, "Alice private dashboard task", TODAY, TaskPriority.HIGH, TaskStatus.TODO);
        createTask(bob, "Bob private dashboard task", TODAY, TaskPriority.HIGH, TaskStatus.TODO);

        mvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/my-day")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
        mvc.perform(get("/dashboard").with(user(alice.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alice private dashboard task")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Bob private dashboard task"))));
        mvc.perform(get("/my-day").with(user(bob.principal())))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bob private dashboard task")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Alice private dashboard task"))));
    }

    private UUID createTask(TestUser owner, String title, LocalDate dueDate, TaskPriority priority, TaskStatus status) {
        TaskForm form = new TaskForm();
        form.setTitle(title);
        form.setDueDate(dueDate);
        form.setPriority(priority);
        form.setStatus(status);
        return tasks.create(owner.account().getId(), form);
    }

    private TestUser createUser(String name, String zone) {
        String email = name.toLowerCase() + "-" + UUID.randomUUID() + "@example.test";
        UserAccount account = registration.register(name, email, PASSWORD);
        profiles.updatePreferences(account.getId(), name, ThemePreference.SYSTEM, zone, "en");
        return new TestUser(account, (TaskOsUserDetails) userDetails.loadUserByUsername(email));
    }

    private void setCompletedAt(UUID taskId, Instant completedAt) {
        jdbc.update("update task_os.tasks set completed_at = ? where id = ?",
                java.sql.Timestamp.from(completedAt), taskId);
    }

    private record TestUser(UserAccount account, TaskOsUserDetails principal) { }

    static final class AdjustableClock extends Clock {
        private volatile Instant instant = DEFAULT_NOW;

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant, zone); }
        @Override public Instant instant() { return instant; }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClockConfiguration {
        @Bean
        @Primary
        AdjustableClock dashboardTestClock() {
            return new AdjustableClock();
        }
    }

    private static final class TaskListPageAssertions {
        private static void assertTitles(List<DashboardTask> tasks, List<String> expected) {
            assertEquals(expected, tasks.stream().map(DashboardTask::title).toList());
        }
    }
}
