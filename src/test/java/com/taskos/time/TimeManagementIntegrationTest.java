package com.taskos.time;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.taskos.auth.service.RegistrationService;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.service.TaskService;
import com.taskos.time.dto.FocusStartForm;
import com.taskos.time.dto.ManualTimeEntryForm;
import com.taskos.time.dto.RecurrenceForm;
import com.taskos.time.dto.ReminderForm;
import com.taskos.time.dto.TimeBlockForm;
import com.taskos.time.dto.TrackingStartForm;
import com.taskos.time.entity.FocusMode;
import com.taskos.time.entity.FocusState;
import com.taskos.time.entity.TimeEntryStatus;
import com.taskos.time.repository.FocusSessionRepository;
import com.taskos.time.repository.TimeEntryRepository;
import com.taskos.time.repository.TimeEntrySegmentRepository;
import com.taskos.time.service.FocusSessionService;
import com.taskos.time.service.RecurrenceService;
import com.taskos.time.service.ReminderService;
import com.taskos.time.service.TimeBlockService;
import com.taskos.time.service.TimeRuleException;
import com.taskos.time.service.TimeTrackingService;
import com.taskos.time.service.TimeInsightsService;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class TimeManagementIntegrationTest {
    @Autowired private RegistrationService registration;
    @Autowired private TimeBlockService blocks;
    @Autowired private TimeTrackingService tracking;
    @Autowired private FocusSessionService focus;
    @Autowired private TimeEntryRepository entries;
    @Autowired private TimeEntrySegmentRepository segments;
    @Autowired private FocusSessionRepository sessions;
    @Autowired private TaskService tasks;
    @Autowired private RecurrenceService recurrences;
    @Autowired private ReminderService reminders;
    @Autowired private TimeInsightsService insights;

    private UUID owner;
    private UUID otherOwner;

    @BeforeEach
    void createOwners() {
        owner = registration.register("Time Owner", "time-owner-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase").getId();
        otherOwner = registration.register("Other Owner", "time-other-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase").getId();
    }

    @Test
    void overlappingReservationsWarnAndRecordsRemainOwnerScoped() {
        TimeBlockForm form = block("Planning", LocalDateTime.now().plusDays(1).withHour(9));
        UUID blockId = blocks.create(owner, form).id();
        TimeBlockForm overlap = block("Second plan", form.getStartAt().plusMinutes(30));
        assertTrue(blocks.create(owner, overlap).conflictWarning());
        assertThrows(TimeRuleException.class, () -> blocks.get(otherOwner, blockId));
    }

    @Test
    void workTimerPersistsPauseResumeAndAllowsOnlyOneActiveTimer() {
        UUID entryId = tracking.start(owner, new TrackingStartForm());
        assertThrows(TimeRuleException.class, () -> tracking.start(owner, new TrackingStartForm()));
        assertThrows(TimeRuleException.class, () -> tracking.pause(otherOwner, entryId));
        tracking.pause(owner, entryId);
        assertEquals(TimeEntryStatus.PAUSED, entries.findByIdAndOwnerId(entryId, owner).orElseThrow().getStatus());
        tracking.resume(owner, entryId);
        tracking.stop(owner, entryId);
        assertEquals(TimeEntryStatus.COMPLETED, entries.findByIdAndOwnerId(entryId, owner).orElseThrow().getStatus());
        var persistedSegments = segments.findAllByEntryIdOrderByStartedAtAsc(entryId);
        assertEquals(2, persistedSegments.size());
        assertTrue(persistedSegments.stream().allMatch(segment -> segment.getStartedAt().isBefore(segment.getEndedAt())));
    }

    @Test
    void focusSessionLifecycleIsPersistedAndOwnerScoped() {
        FocusStartForm form = new FocusStartForm();
        form.setMode(FocusMode.DEEP_WORK);
        form.setFocusMinutes(1);
        UUID sessionId = focus.start(owner, form);
        assertThrows(TimeRuleException.class, () -> focus.stop(otherOwner, sessionId));
        focus.pause(owner, sessionId);
        focus.resume(owner, sessionId);
        focus.stop(owner, sessionId);
        var session = sessions.findByIdAndOwnerId(sessionId, owner).orElseThrow();
        assertEquals(FocusState.CANCELLED, session.getState());
        assertNotNull(session.getCompletedAt());
    }

    @Test
    void dueRecurrencesCreateCanonicalTasksOnceAndDueRemindersCreateInboxItemsOnce() {
        TaskForm taskForm = new TaskForm(); taskForm.setTitle("Daily review");
        UUID templateId = tasks.create(owner, taskForm);
        LocalDate yesterday = LocalDate.now(ZoneOffset.UTC).minusDays(1);
        RecurrenceForm recurrence = new RecurrenceForm(); recurrence.setSourceTaskId(templateId);
        recurrence.setFrequency(com.taskos.time.entity.RecurrenceFrequency.DAILY);
        recurrence.setStartDate(yesterday); recurrence.setLocalTime(LocalTime.MIDNIGHT);
        UUID ruleId = recurrences.create(owner, recurrence);
        assertTrue(recurrences.processRule(owner, ruleId, java.time.Instant.now()) > 0);
        assertEquals(0, recurrences.processRule(owner, ruleId, java.time.Instant.now()));
        assertTrue(recurrences.recentOccurrences(owner).size() > 0);

        ReminderForm reminder = new ReminderForm(); reminder.setTaskId(templateId); reminder.setTitle("Review now");
        reminder.setScheduledFor(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        reminders.create(owner, reminder);
        reminders.processDue(); reminders.processDue();
        assertEquals(1, reminders.inbox(owner).size());
    }

    @Test
    void taskInsightsUseTrackedSegmentsAndKeepPersonalTimeOutOfTaskAttribution() {
        TaskForm taskForm = new TaskForm(); taskForm.setTitle("Write proposal");
        UUID taskId = tasks.create(owner, taskForm);
        LocalDateTime end = LocalDateTime.now(ZoneOffset.UTC).withSecond(0).withNano(0);
        ManualTimeEntryForm manual = new ManualTimeEntryForm(); manual.setTaskId(taskId);
        manual.setDescription("Drafting"); manual.setStartAt(end.minusMinutes(45)); manual.setEndAt(end);
        tracking.addManual(owner, manual);

        var taskTotal = insights.page(owner).byTask().stream().filter(total -> total.taskId().equals(taskId)).findFirst().orElseThrow();
        assertTrue(taskTotal.seconds() >= 44 * 60);
        assertNull(taskTotal.projectName());
    }

    private static TimeBlockForm block(String title, LocalDateTime start) {
        TimeBlockForm form = new TimeBlockForm(); form.setTitle(title); form.setStartAt(start);
        form.setEndAt(start.plusHours(1)); return form;
    }
}
