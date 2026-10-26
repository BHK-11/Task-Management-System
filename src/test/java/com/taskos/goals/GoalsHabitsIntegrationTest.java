package com.taskos.goals;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.taskos.auth.service.RegistrationService;
import com.taskos.goals.dto.GoalForm;
import com.taskos.goals.dto.KeyResultForm;
import com.taskos.goals.dto.KeyResultMeasurementForm;
import com.taskos.goals.dto.OkrForm;
import com.taskos.goals.entity.GoalMetric;
import com.taskos.goals.service.GoalService;
import com.taskos.goals.service.OkrService;
import com.taskos.habits.dto.HabitForm;
import com.taskos.habits.entity.HabitFrequency;
import com.taskos.habits.service.HabitService;
import com.taskos.time.service.TimeZoneResolver;
import com.taskos.time.service.ReminderService;
import com.taskos.time.service.TimeRuleException;
import com.taskos.time.repository.ReminderRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class GoalsHabitsIntegrationTest {
    @Autowired private RegistrationService registration;
    @Autowired private GoalService goals;
    @Autowired private OkrService okrs;
    @Autowired private HabitService habits;
    @Autowired private TimeZoneResolver zones;
    @Autowired private ReminderService reminders;
    @Autowired private ReminderRepository reminderRows;
    @Autowired private Clock clock;
    private UUID owner;
    private UUID otherOwner;

    @BeforeEach
    void createOwners() {
        owner = registration.register("Growth Owner", "growth-owner-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase").getId();
        otherOwner = registration.register("Other Owner", "growth-other-" + UUID.randomUUID() + "@example.test",
                "a sufficiently long test passphrase").getId();
    }

    @Test
    void decreasingGoalMeasurementsArePersistedAndOwnerScoped() {
        LocalDate today = LocalDate.now(clock.withZone(zones.zone(owner)));
        GoalForm form = new GoalForm(); form.setTitle("Reduce response time"); form.setMetricType(GoalMetric.NUMERIC_DECREASE);
        form.setBaselineValue(new BigDecimal("10")); form.setTargetValue(new BigDecimal("2"));
        form.setStartDate(today.minusDays(7));
        UUID id = goals.create(owner, form);
        goals.record(owner, id, new BigDecimal("4"), today, "Measured from the service report");

        var detail = goals.detail(owner, id);
        assertEquals(75, detail.progress().displayPercent());
        assertEquals(2, detail.measurements().size());
        assertThrows(IllegalArgumentException.class, () -> goals.detail(otherOwner, id));
        assertThrows(IllegalArgumentException.class, () -> goals.record(otherOwner, id, BigDecimal.ONE, today, ""));
    }

    @Test
    void okrDoesNotTreatMissingMeasurementAsZeroAndRecordsActualProgress() {
        LocalDate today = LocalDate.now(clock.withZone(zones.zone(owner)));
        YearMonth month = YearMonth.from(today);
        OkrForm objective = new OkrForm(); objective.setTitle("Improve service reliability");
        objective.setCycleName("Current month " + month); objective.setCycleStart(month.atDay(1)); objective.setCycleEnd(month.atEndOfMonth());
        UUID id = okrs.createObjective(owner, objective);
        KeyResultForm result = new KeyResultForm(); result.setTitle("Requests meeting the target");
        result.setBaseline(new BigDecimal("10")); result.setTarget(new BigDecimal("20")); result.setUnit("% ");
        UUID resultId = okrs.addKeyResult(owner, id, result);
        assertNull(okrs.detail(owner, id).progress());

        KeyResultMeasurementForm measurement = new KeyResultMeasurementForm(); measurement.setValue(new BigDecimal("15")); measurement.setDate(today);
        okrs.record(owner, id, resultId, measurement);
        assertEquals(50, okrs.detail(owner, id).displayPercent());
        assertThrows(IllegalArgumentException.class, () -> okrs.detail(otherOwner, id));
    }

    @Test
    void selectedWeekdayStreakIgnoresUnscheduledDatesAndLeavesTodayOpen() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("UTC")));
        LocalDate start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(2);
        HabitForm selected = habit("Walk outdoors", start, HabitFrequency.SELECTED_DAYS);
        selected.setSelectedWeekdays("1,5");
        UUID id = habits.create(owner, selected);
        int completedScheduledDates = 0;
        for (LocalDate day = start; !day.isAfter(today); day = day.plusDays(1)) {
            if (day.getDayOfWeek() == DayOfWeek.MONDAY || day.getDayOfWeek() == DayOfWeek.FRIDAY) {
                habits.checkIn(owner, id, day, null, true, ""); completedScheduledDates++;
            }
        }
        assertEquals(completedScheduledDates, habits.stats(owner, id).currentStreak());
        assertEquals(completedScheduledDates, habits.stats(owner, id).longestStreak());
        assertThrows(IllegalArgumentException.class, () -> habits.detail(otherOwner, id));

        HabitForm daily = habit("Read", today.minusDays(1), HabitFrequency.DAILY);
        UUID dailyId = habits.create(owner, daily);
        habits.checkIn(owner, dailyId, today.minusDays(1), null, true, "");
        assertEquals(1, habits.stats(owner, dailyId).currentStreak());
        assertEquals(0, habits.stats(owner, dailyId).missedPeriods());
        assertEquals(1, habits.stats(owner, dailyId).openPeriods());
    }

    @Test
    void weeklyAndMonthlyTargetsCountSuccessfulPeriodsRatherThanRawCheckIns() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("UTC")));
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(3);
        HabitForm weekly = habit("Weekly practice", monday, HabitFrequency.WEEKLY_TARGET); weekly.setTargetCount(2);
        UUID weeklyId = habits.create(owner, weekly);
        int successfulWeeks = 0;
        for (LocalDate week = monday; !week.isAfter(today); week = week.plusWeeks(1)) {
            int logged = 0;
            for (LocalDate day : new LocalDate[] { week, week.plusDays(2) }) if (!day.isAfter(today)) {
                habits.checkIn(owner, weeklyId, day, null, true, ""); logged++;
            }
            if (logged >= 2) successfulWeeks++;
        }
        var weeklyStats = habits.stats(owner, weeklyId);
        assertEquals(successfulWeeks, weeklyStats.currentStreak());
        assertEquals(successfulWeeks, weeklyStats.longestStreak());

        LocalDate firstOfMonth = today.withDayOfMonth(1).minusMonths(3);
        HabitForm monthly = habit("Monthly practice", firstOfMonth, HabitFrequency.MONTHLY_TARGET); monthly.setTargetCount(3);
        UUID monthlyId = habits.create(owner, monthly);
        int successfulMonths = 0;
        for (YearMonth month = YearMonth.from(firstOfMonth); !month.atDay(1).isAfter(today); month = month.plusMonths(1)) {
            int logged = 0;
            for (int day = 1; day <= 3 && !month.atDay(day).isAfter(today); day++) {
                habits.checkIn(owner, monthlyId, month.atDay(day), null, true, ""); logged++;
            }
            if (logged >= 3) successfulMonths++;
        }
        var monthlyStats = habits.stats(owner, monthlyId);
        assertEquals(successfulMonths, monthlyStats.currentStreak());
        assertEquals(successfulMonths, monthlyStats.longestStreak());
    }

    @Test
    void quantityTargetsAggregateWithinTheScheduleAndFutureChangesKeepOldRules() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("UTC")));
        LocalDate pastMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(2);
        HabitForm weekly = habit("Weekly reading", pastMonday, HabitFrequency.WEEKLY_TARGET);
        weekly.setTargetQuantity(new BigDecimal("30")); weekly.setUnit("minutes");
        UUID id = habits.create(owner, weekly);
        habits.checkIn(owner, id, pastMonday, new BigDecimal("10"), true, "");
        habits.checkIn(owner, id, pastMonday.plusDays(2), new BigDecimal("20"), true, "");
        assertEquals(1, habits.stats(owner, id).successfulPeriods());

        HabitForm changed = habit("Weekly reading", pastMonday, HabitFrequency.SELECTED_DAYS);
        changed.setSelectedWeekdays("1,3,5"); changed.setEffectiveDate(today.with(TemporalAdjusters.next(DayOfWeek.MONDAY)));
        habits.update(owner, id, changed);
        var rules = habits.schedules(owner, id);
        assertEquals(2, rules.size());
        assertEquals(HabitFrequency.WEEKLY_TARGET, rules.getFirst().getFrequency());
        assertEquals(HabitFrequency.SELECTED_DAYS, rules.getLast().getFrequency());
        assertThrows(IllegalArgumentException.class, () -> habits.detail(otherOwner, id));
    }

    @Test
    void pausingExcludesTheCurrentPeriodWithoutErasingThePriorStreak() {
        LocalDate today = LocalDate.now(clock.withZone(ZoneId.of("UTC")));
        HabitForm daily = habit("Stretch", today.minusDays(2), HabitFrequency.DAILY);
        UUID id = habits.create(owner, daily);
        habits.checkIn(owner, id, today.minusDays(2), null, true, "");
        habits.checkIn(owner, id, today.minusDays(1), null, true, "");
        habits.pause(owner, id);
        var paused = habits.stats(owner, id);
        assertEquals(2, paused.currentStreak());
        assertEquals(0, paused.missedPeriods());
        assertEquals(2, paused.eligibleClosedPeriods());

        habits.resume(owner, id);
        assertEquals(today, habits.pauses(owner, id).getFirst().getEndDate());
        assertEquals(2, habits.stats(owner, id).currentStreak());
    }

    @Test
    void goalAndHabitRemindersPersistThroughTheExistingOwnerScopedInbox() {
        LocalDate today = LocalDate.now(clock.withZone(zones.zone(owner)));
        GoalForm goal = new GoalForm(); goal.setTitle("Finish the course"); goal.setMetricType(GoalMetric.MANUAL_STATUS); goal.setStartDate(today);
        UUID goalId = goals.create(owner, goal);
        UUID habitId = habits.create(owner, habit("Study", today, HabitFrequency.DAILY));
        var reminderTime = java.time.LocalDateTime.now(clock.withZone(zones.zone(owner))).plusHours(1);
        UUID goalReminder = reminders.createGoalCheckIn(owner, goalId, reminderTime);
        UUID habitReminder = reminders.createHabitCheckIn(owner, habitId, reminderTime);

        assertEquals(goalId, reminderRows.findByIdAndOwnerId(goalReminder, owner).orElseThrow().getGoalId());
        assertEquals(habitId, reminderRows.findByIdAndOwnerId(habitReminder, owner).orElseThrow().getHabitId());
        assertThrows(TimeRuleException.class, () -> reminders.createGoalCheckIn(otherOwner, goalId, reminderTime));
        assertThrows(TimeRuleException.class, () -> reminders.createHabitCheckIn(otherOwner, habitId, reminderTime));
    }

    private static HabitForm habit(String name, LocalDate start, HabitFrequency frequency) {
        HabitForm form = new HabitForm(); form.setName(name); form.setStartDate(start);
        form.setTimezone("UTC"); form.setFrequency(frequency); return form;
    }
}
