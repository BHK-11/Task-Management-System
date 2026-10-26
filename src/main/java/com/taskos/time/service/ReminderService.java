package com.taskos.time.service;

import com.taskos.task.repository.TaskRepository;
import com.taskos.time.dto.ReminderForm;
import com.taskos.time.entity.InAppNotification;
import com.taskos.time.entity.Reminder;
import com.taskos.time.entity.ReminderStatus;
import com.taskos.time.repository.InAppNotificationRepository;
import com.taskos.time.repository.ReminderRepository;
import com.taskos.time.repository.TimeBlockRepository;
import com.taskos.goals.repository.GoalRepository;
import com.taskos.goals.entity.GoalStatus;
import com.taskos.habits.repository.HabitRepository;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReminderService {
    private final ReminderRepository reminders;
    private final InAppNotificationRepository notifications;
    private final TaskRepository tasks;
    private final TimeBlockRepository blocks;
    private final GoalRepository goals;
    private final HabitRepository habits;
    private final UserAccountRepository users;
    private final TimeZoneResolver zones;
    private final Clock clock;

    public ReminderService(ReminderRepository reminders, InAppNotificationRepository notifications, TaskRepository tasks,
            TimeBlockRepository blocks, GoalRepository goals, HabitRepository habits, UserAccountRepository users, TimeZoneResolver zones, Clock clock) {
        this.reminders = reminders; this.notifications = notifications; this.tasks = tasks; this.blocks = blocks;
        this.goals = goals; this.habits = habits;
        this.users = users; this.zones = zones; this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Reminder> list(UUID ownerId) { return reminders.findAllByOwnerIdOrderByScheduledForAsc(ownerId); }
    @Transactional(readOnly = true)
    public List<InAppNotification> inbox(UUID ownerId) { return notifications.findAllByOwnerIdOrderByDeliveredAtDesc(ownerId, PageRequest.of(0, 100)); }
    @Transactional(readOnly = true)
    public long unreadCount(UUID ownerId) { return notifications.countByOwnerIdAndReadAtIsNull(ownerId); }
    @Transactional(readOnly = true)
    public long pendingCount(UUID ownerId) { return reminders.countByOwnerIdAndStatus(ownerId, ReminderStatus.PENDING); }

    @Transactional
    public UUID create(UUID ownerId, ReminderForm form) {
        var user = users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        boolean task = form.getTaskId() != null, block = form.getTimeBlockId() != null;
        if (task == block) throw new TimeRuleException("Choose either a task or a time block for this reminder.");
        if (task && tasks.findDetailByIdAndOwner(form.getTaskId(), ownerId).isEmpty()) throw new TimeRuleException("Choose one of your own tasks.");
        if (block && blocks.findByIdAndOwnerId(form.getTimeBlockId(), ownerId).isEmpty()) throw new TimeRuleException("Choose one of your own time blocks.");
        ZoneId zone = zones.zone(ownerId); var scheduled = TimeBlockService.resolve(form.getScheduledFor(), zone);
        if (form.getTitle() == null || form.getTitle().isBlank() || form.getTitle().strip().length() > 160)
            throw new TimeRuleException("Enter a reminder title using 160 characters or fewer.");
        return reminders.save(Reminder.create(user.getId(), form.getTaskId(), form.getTimeBlockId(),
                form.getTitle().strip(), scheduled, clock.instant())).getId();
    }

    @Transactional
    public UUID createFromAutomation(UUID ownerId, UUID taskId, String title, int offsetMinutes) {
        var task = tasks.findDetailByIdAndOwner(taskId, ownerId)
                .orElseThrow(() -> new TimeRuleException("The task is no longer available to this automation."));
        if (task.getArchivedAt() != null || task.getDueDate() == null)
            throw new TimeRuleException("A reminder action needs an unarchived task with a due date.");
        if (title == null || title.isBlank() || title.strip().length() > 160 || offsetMinutes < 1 || offsetMinutes > 525600)
            throw new TimeRuleException("The reminder action is invalid.");
        var now = clock.instant();
        var scheduled = task.getDueDate().atTime(9, 0).atZone(zones.zone(ownerId)).toInstant()
                .minus(Duration.ofMinutes(offsetMinutes));
        return reminders.save(Reminder.create(ownerId, taskId, null, title.strip(), scheduled, now)).getId();
    }

    @Transactional
    public UUID createGoalCheckIn(UUID ownerId, UUID goalId, LocalDateTime scheduledFor) {
        var goal = goals.findByIdAndOwnerId(goalId, ownerId).orElseThrow(() -> new TimeRuleException("That goal is not available."));
        if (goal.getArchivedAt() != null || goal.getStatus() == GoalStatus.CANCELLED || goal.getStatus() == GoalStatus.COMPLETED)
            throw new TimeRuleException("Only an unarchived, active or paused goal can have a check-in reminder.");
        if (scheduledFor == null) throw new TimeRuleException("Choose a reminder time.");
        var when = TimeBlockService.resolve(scheduledFor, zones.zone(ownerId));
        String title = "Goal check-in: " + goal.getTitle();
        return reminders.save(Reminder.forGoal(ownerId, goalId, title.substring(0, Math.min(160, title.length())), when, clock.instant())).getId();
    }

    @Transactional
    public UUID createHabitCheckIn(UUID ownerId, UUID habitId, LocalDateTime scheduledFor) {
        var habit = habits.findByIdAndOwnerId(habitId, ownerId).orElseThrow(() -> new TimeRuleException("That habit is not available."));
        if (!"ACTIVE".equals(habit.getStatus())) throw new TimeRuleException("Archived habits cannot have reminders.");
        if (scheduledFor == null) throw new TimeRuleException("Choose a reminder time.");
        var when = TimeBlockService.resolve(scheduledFor, zones.zone(ownerId));
        String title = "Habit check-in: " + habit.getName();
        return reminders.save(Reminder.forHabit(ownerId, habitId, title.substring(0, Math.min(160, title.length())), when, clock.instant())).getId();
    }

    @Transactional
    public void processDue() {
        var due = reminders.findDue(clock.instant(), PageRequest.of(0, 100));
        for (Reminder candidate : due) {
            Reminder reminder = reminders.findLockedById(candidate.getId()).orElse(null);
            if (reminder == null || reminder.getStatus() != ReminderStatus.PENDING || reminder.getScheduledFor().isAfter(clock.instant())) continue;
            var prior = notifications.findByReminderId(reminder.getId());
            if (prior.isPresent()) prior.get().redeliver(clock.instant());
            else notifications.save(InAppNotification.deliver(reminder.getOwnerId(), reminder.getId(), reminder.getTitle(), reminder.getTaskId(), clock.instant()));
            reminder.deliver(clock.instant());
        }
    }

    @Transactional
    public void snooze(UUID ownerId, UUID id, java.time.LocalDateTime localTime) {
        Reminder reminder = ownReminder(ownerId, id); var next = TimeBlockService.resolve(localTime, zones.zone(ownerId));
        var now = clock.instant();
        if (!next.isAfter(now) || Duration.between(now, next).compareTo(Duration.ofDays(30)) > 0)
            throw new TimeRuleException("Choose a snooze time within the next 30 days.");
        reminder.snooze(next);
    }
    @Transactional
    public void dismiss(UUID ownerId, UUID id) { ownReminder(ownerId, id).dismiss(clock.instant()); }
    @Transactional
    public void read(UUID ownerId, UUID notificationId) {
        InAppNotification notification = notifications.findByIdAndOwnerId(notificationId, ownerId)
                .orElseThrow(() -> new TimeRuleException("That notification is not available."));
        notification.markRead(clock.instant());
        reminders.findByIdAndOwnerId(notification.getReminderId(), ownerId).ifPresent(item -> item.markRead(clock.instant()));
    }

    private Reminder ownReminder(UUID ownerId, UUID id) {
        users.findLockedById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        return reminders.findLockedById(id).filter(reminder -> reminder.getOwnerId().equals(ownerId))
                .orElseThrow(() -> new TimeRuleException("That reminder is not available."));
    }
}
