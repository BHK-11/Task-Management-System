package com.taskos.analytics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.taskos.analytics.service.AnalyticsService;
import com.taskos.auth.service.RegistrationService;
import com.taskos.security.service.DatabaseUserDetailsService;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.entity.TaskStatus;
import com.taskos.task.service.TaskService;
import com.taskos.time.service.TimeZoneResolver;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AnalyticsIntegrationTest {
    @Autowired private MockMvc mvc;
    @Autowired private RegistrationService registration;
    @Autowired private DatabaseUserDetailsService users;
    @Autowired private TaskService tasks;
    @Autowired private AnalyticsService analytics;
    @Autowired private TimeZoneResolver zones;

    @Test
    void analyticsUsesRetainedOwnerScopedTasksAndCompletionEvents() throws Exception {
        var alice = registration.register("Analytics Alice", "analytics-alice-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        var bob = registration.register("Analytics Bob", "analytics-bob-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        LocalDate today = LocalDate.now(zones.zone(alice.getId()));
        create(alice.getId(), "Overdue open work", today.minusDays(1), TaskStatus.TODO);
        create(alice.getId(), "Unscheduled open work", null, TaskStatus.ON_HOLD);
        create(alice.getId(), "Completed today", today, TaskStatus.COMPLETED);
        create(bob.getId(), "Private Bob completion", today, TaskStatus.COMPLETED);

        var data = analytics.overview(alice.getId(), today, today, null);

        assertEquals(3, data.createdTasks());
        assertEquals(1, data.completedTasks());
        assertEquals(1, data.completionEvents());
        assertEquals(2, data.openTasks());
        assertEquals(1, data.overdueTasks());
        assertEquals(1, data.noDueDateTasks());
        assertEquals(1, data.completionTrend().stream().mapToLong(point -> point.count()).sum());

        var principal = users.loadUserByUsername(alice.getEmail());
        mvc.perform(get("/analytics").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Completion events")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Status distribution")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Metric registry")));
        mvc.perform(get("/api/analytics/overview").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("completionEvents")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"version\":\"1.0\"")));
        mvc.perform(get("/analytics/export.csv").with(user(principal)))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("TASK_COMPLETION_EVENTS")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("definition_version")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(",\"1.0\"")));
    }

    @Test
    void projectFilterCannotCrossPersonalOwnershipAndDateRangesAreBounded() {
        var owner = registration.register("Analytics Filter Owner", "analytics-filter-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase");
        ResponseStatusException notFound = org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> analytics.overview(owner.getId(), null, null, UUID.randomUUID()));
        assertEquals(404, notFound.getStatusCode().value());
        LocalDate today = LocalDate.now(zones.zone(owner.getId()));
        org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> analytics.overview(owner.getId(), today.plusDays(1), today, null));
        org.junit.jupiter.api.Assertions.assertThrows(ResponseStatusException.class,
                () -> analytics.overview(owner.getId(), today.minusDays(366), today, null));
    }

    private void create(UUID ownerId, String title, LocalDate due, TaskStatus status) {
        TaskForm form = new TaskForm();
        form.setTitle(title);
        form.setDueDate(due);
        form.setPriority(TaskPriority.MEDIUM);
        form.setStatus(status);
        tasks.create(ownerId, form);
    }
}
