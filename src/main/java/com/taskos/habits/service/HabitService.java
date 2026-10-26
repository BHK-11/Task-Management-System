package com.taskos.habits.service;

import com.taskos.goals.repository.GoalRepository;
import com.taskos.habits.dto.HabitDay;
import com.taskos.habits.dto.HabitDetail;
import com.taskos.habits.dto.HabitForm;
import com.taskos.habits.dto.HabitStats;
import com.taskos.habits.entity.Habit;
import com.taskos.habits.entity.HabitCheckIn;
import com.taskos.habits.entity.HabitFrequency;
import com.taskos.habits.entity.HabitPause;
import com.taskos.habits.entity.HabitSchedule;
import com.taskos.habits.repository.HabitCheckInRepository;
import com.taskos.habits.repository.HabitPauseRepository;
import com.taskos.habits.repository.HabitRepository;
import com.taskos.habits.repository.HabitScheduleRepository;
import com.taskos.task.repository.TaskRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class HabitService {
    private final HabitRepository habits;
    private final HabitScheduleRepository schedules;
    private final HabitCheckInRepository checkIns;
    private final HabitPauseRepository pauses;
    private final GoalRepository goals;
    private final TaskRepository tasks;
    private final Clock clock;

    public HabitService(HabitRepository habits, HabitScheduleRepository schedules, HabitCheckInRepository checkIns,
            HabitPauseRepository pauses, GoalRepository goals, TaskRepository tasks, Clock clock) {
        this.habits = habits; this.schedules = schedules; this.checkIns = checkIns; this.pauses = pauses;
        this.goals = goals; this.tasks = tasks; this.clock = clock;
    }

    @Transactional(readOnly = true) public List<Habit> list(UUID ownerId) { return habits.findAllByOwnerIdAndStatusOrderByNameAsc(ownerId, "ACTIVE"); }
    @Transactional(readOnly = true) public long activeCount(UUID ownerId) { return habits.countByOwnerIdAndStatus(ownerId, "ACTIVE"); }

    @Transactional
    public UUID create(UUID ownerId, HabitForm form) {
        validate(form, ownerId, true);
        Habit habit = habits.save(Habit.create(ownerId, form.getName(), form.getDescription(), form.getStartDate(), form.getTimezone(), form.getLinkedGoalId(), form.getLinkedTaskId(), clock.instant()));
        schedules.save(schedule(ownerId, habit.getId(), form.getStartDate(), form)); return habit.getId();
    }

    @Transactional
    public void update(UUID ownerId, UUID id, HabitForm form) {
        Habit habit = ownLockedHabit(ownerId, id); validate(form, ownerId, false);
        LocalDate effective = form.getEffectiveDate(); LocalDate today = today(habit);
        if (effective == null || !effective.isAfter(today)) throw new IllegalArgumentException("A new schedule must start after today so past periods keep their original rules.");
        if (form.getFrequency() == HabitFrequency.WEEKLY_TARGET && effective.getDayOfWeek() != DayOfWeek.MONDAY)
            throw new IllegalArgumentException("Weekly target changes must start on a Monday.");
        if (form.getFrequency() == HabitFrequency.MONTHLY_TARGET && effective.getDayOfMonth() != 1)
            throw new IllegalArgumentException("Monthly target changes must start on the first day of a month.");
        habit.update(form.getName(), form.getDescription(), form.getLinkedGoalId(), form.getLinkedTaskId(), clock.instant());
        schedules.findByOwnerIdAndHabitIdAndEffectiveFrom(ownerId, id, effective).ifPresent(schedules::delete);
        schedules.save(schedule(ownerId, id, effective, form));
    }

    @Transactional
    public void checkIn(UUID ownerId, UUID id, LocalDate date, BigDecimal quantity, boolean performed, String notes) {
        Habit habit = habits.findLockedByIdAndOwnerId(id, ownerId).orElseThrow(() -> new IllegalArgumentException("That habit is not available."));
        LocalDate today = today(habit);
        if (!"ACTIVE".equals(habit.getStatus())) throw new IllegalArgumentException("Archived habits cannot be checked in.");
        if (date == null || date.isAfter(today) || date.isBefore(habit.getStartDate())) throw new IllegalArgumentException("Choose a habit date between its start and today.");
        if (isPaused(ownerId, id, date)) throw new IllegalArgumentException("This habit was paused on that date.");
        HabitSchedule schedule = scheduleFor(ownerId, id, date);
        if (schedule == null || (schedule.getFrequency() != HabitFrequency.WEEKLY_TARGET && schedule.getFrequency() != HabitFrequency.MONTHLY_TARGET && !isScheduled(schedule, date)))
            throw new IllegalArgumentException("That date was not scheduled for this habit.");
        if (quantity != null && quantity.signum() < 0) throw new IllegalArgumentException("Quantity cannot be negative.");
        if (notes != null && notes.length() > 600) throw new IllegalArgumentException("Notes must be 600 characters or fewer.");
        checkIns.findByOwnerIdAndHabitIdAndLocalDate(ownerId, id, date)
                .ifPresentOrElse(item -> item.update(quantity, performed, notes, clock.instant()),
                        () -> checkIns.save(HabitCheckIn.record(ownerId, id, date, quantity, performed, notes, clock.instant())));
    }

    @Transactional
    public void pause(UUID ownerId, UUID id) {
        Habit habit = ownLockedHabit(ownerId, id); LocalDate today = today(habit);
        if (pauses.findFirstByOwnerIdAndHabitIdAndEndDateIsNullOrderByStartDateDesc(ownerId, id).isPresent()) return;
        pauses.save(HabitPause.start(ownerId, id, today, clock.instant()));
    }

    @Transactional
    public void resume(UUID ownerId, UUID id) {
        Habit habit = ownLockedHabit(ownerId, id); LocalDate today = today(habit);
        HabitPause pause = pauses.findFirstByOwnerIdAndHabitIdAndEndDateIsNullOrderByStartDateDesc(ownerId, id)
                .orElseThrow(() -> new IllegalArgumentException("That habit is not paused."));
        pause.resume(today);
    }

    @Transactional public void archive(UUID ownerId, UUID id) { ownLockedHabit(ownerId, id).archive(clock.instant()); }

    @Transactional(readOnly = true)
    public HabitDetail detail(UUID ownerId, UUID id) {
        Habit habit = ownHabit(ownerId, id); List<HabitSchedule> rules = schedules.findAllByOwnerIdAndHabitIdOrderByEffectiveFromAsc(ownerId, id);
        List<HabitPause> pausePeriods = pauses.findAllByOwnerIdAndHabitIdOrderByStartDateAsc(ownerId, id);
        LocalDate today = today(habit); LocalDate from = habit.getStartDate();
        List<HabitCheckIn> history = checkIns.findAllByOwnerIdAndHabitIdOrderByLocalDateDesc(ownerId, id);
        List<Period> periods = periods(habit, rules, pausePeriods, history, today);
        List<HabitDay> calendar = calendar(habit, rules, pausePeriods, history, today);
        return new HabitDetail(habit, stats(periods), history.stream().limit(60).toList(), rules, pausePeriods, calendar);
    }

    @Transactional(readOnly = true)
    public HabitStats stats(UUID ownerId, UUID id) {
        Habit habit = ownHabit(ownerId, id); LocalDate today = today(habit);
        return stats(periods(habit, schedules.findAllByOwnerIdAndHabitIdOrderByEffectiveFromAsc(ownerId, id),
                pauses.findAllByOwnerIdAndHabitIdOrderByStartDateAsc(ownerId, id),
                checkIns.findAllByOwnerIdAndHabitIdOrderByLocalDateDesc(ownerId, id), today));
    }

    @Transactional(readOnly = true) public List<HabitSchedule> schedules(UUID ownerId, UUID id) { ownHabit(ownerId, id); return schedules.findAllByOwnerIdAndHabitIdOrderByEffectiveFromAsc(ownerId, id); }
    @Transactional(readOnly = true) public List<HabitPause> pauses(UUID ownerId, UUID id) { ownHabit(ownerId, id); return pauses.findAllByOwnerIdAndHabitIdOrderByStartDateAsc(ownerId, id); }

    private List<Period> periods(Habit habit, List<HabitSchedule> rules, List<HabitPause> pauseList, List<HabitCheckIn> history, LocalDate today) {
        if (rules.isEmpty() || today.isBefore(habit.getStartDate())) return List.of();
        Map<LocalDate, HabitCheckIn> logs = new HashMap<>(); history.forEach(item -> logs.put(item.getLocalDate(), item));
        Map<String, Period> generated = new LinkedHashMap<>();
        HabitSchedule current = null; int ruleIndex = 0;
        for (LocalDate date = habit.getStartDate(); !date.isAfter(today); date = date.plusDays(1)) {
            while (ruleIndex < rules.size() && !rules.get(ruleIndex).getEffectiveFrom().isAfter(date)) current = rules.get(ruleIndex++);
            if (current == null) continue;
            LocalDate start; LocalDate end;
            switch (current.getFrequency()) {
                case DAILY, SELECTED_DAYS -> { if (!isScheduled(current, date)) continue; start = date; end = date; }
                case WEEKLY_TARGET -> { start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)); end = start.plusDays(6); start = start.isBefore(habit.getStartDate()) ? habit.getStartDate() : start; start = start.isBefore(current.getEffectiveFrom()) ? current.getEffectiveFrom() : start; }
                case MONTHLY_TARGET -> { start = date.withDayOfMonth(1); end = YearMonth.from(date).atEndOfMonth(); start = start.isBefore(habit.getStartDate()) ? habit.getStartDate() : start; start = start.isBefore(current.getEffectiveFrom()) ? current.getEffectiveFrom() : start; }
                default -> { continue; }
            }
            String key = current.getId() + ":" + start;
            generated.putIfAbsent(key, new Period(start, end, current));
        }
        List<Period> result = new ArrayList<>(generated.values());
        for (Period period : result) {
            period.paused = pauseList.stream().anyMatch(p -> overlaps(p, period.start, period.end));
            period.closed = period.end.isBefore(today);
        }
        for (var entry : logs.entrySet()) {
            LocalDate date = entry.getKey(); HabitSchedule rule = applicable(rules, date);
            if (rule == null || isPaused(pauseList, date)) continue;
            LocalDate start = switch (rule.getFrequency()) {
                case DAILY, SELECTED_DAYS -> date;
                case WEEKLY_TARGET -> max(date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)), habit.getStartDate(), rule.getEffectiveFrom());
                case MONTHLY_TARGET -> max(date.withDayOfMonth(1), habit.getStartDate(), rule.getEffectiveFrom());
            };
            Period period = generated.get(rule.getId() + ":" + start);
            if (period == null || period.paused) continue;
            if (rule.getTargetQuantity() == null) { if (entry.getValue().isPerformed()) period.count++; }
            else if (entry.getValue().getQuantity() != null) period.total = period.total.add(entry.getValue().getQuantity());
        }
        for (Period period : result) {
            if (period.paused) continue;
            period.successful = period.schedule.getTargetQuantity() == null
                    ? period.count >= (period.schedule.getFrequency() == HabitFrequency.DAILY || period.schedule.getFrequency() == HabitFrequency.SELECTED_DAYS ? 1 : period.schedule.getTargetCount())
                    : period.total.compareTo(period.schedule.getTargetQuantity()) >= 0;
        }
        return result;
    }

    private List<HabitDay> calendar(Habit habit, List<HabitSchedule> rules, List<HabitPause> pauseList, List<HabitCheckIn> history, LocalDate today) {
        LocalDate from = today.minusDays(20); Map<LocalDate, HabitCheckIn> logs = new HashMap<>(); history.forEach(item -> logs.put(item.getLocalDate(), item));
        List<HabitDay> days = new ArrayList<>();
        for (LocalDate date = from; !date.isAfter(today.plusDays(14)); date = date.plusDays(1)) {
            String state; String label;
            if (date.isAfter(today)) { state = "upcoming"; label = "Upcoming"; }
            else if (isPaused(pauseList, date)) { state = "paused"; label = "Paused"; }
            else {
                HabitSchedule rule = applicable(rules, date);
                HabitCheckIn entry = logs.get(date);
                if (entry != null) {
                    boolean met = rule != null && (rule.getTargetQuantity() == null ? entry.isPerformed() : entry.getQuantity() != null && entry.getQuantity().compareTo(rule.getTargetQuantity()) >= 0);
                    state = met ? "completed" : "partial"; label = met ? "Completed" : "Activity logged";
                } else if (rule != null && isScheduled(rule, date) && date.isBefore(today)) { state = "missed"; label = "Missed scheduled habit"; }
                else if (rule != null && isScheduled(rule, date)) { state = "due"; label = "Due today"; }
                else if (rule != null && (rule.getFrequency() == HabitFrequency.WEEKLY_TARGET || rule.getFrequency() == HabitFrequency.MONTHLY_TARGET)) { state = "window"; label = "Within target period"; }
                else { state = "unscheduled"; label = "Not scheduled"; }
            }
            days.add(new HabitDay(date, state, label));
        }
        return days;
    }

    private HabitStats stats(List<Period> periods) {
        int successes = 0, closedSuccesses = 0, misses = 0, current = 0, longest = 0, run = 0, closed = 0, open = 0;
        for (Period period : periods) {
            if (period.paused) continue;
            if (period.closed) { closed++; if (period.successful) { successes++; closedSuccesses++; } else misses++; }
            else { open++; if (period.successful) successes++; }
            if (!period.closed && !period.successful) continue;
            if (period.successful) { run++; longest = Math.max(longest, run); } else run = 0;
        }
        for (int i = periods.size() - 1; i >= 0; i--) {
            Period period = periods.get(i); if (period.paused) continue;
            if (!period.closed && !period.successful) continue;
            if (period.successful) current++; else break;
        }
        BigDecimal consistency = closed == 0 ? null : BigDecimal.valueOf(closedSuccesses)
                .multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(closed), 1, RoundingMode.HALF_UP);
        return new HabitStats(successes, misses, current, longest, consistency, closed, open);
    }

    private void validate(HabitForm form, UUID ownerId, boolean creating) {
        if (form.getName() == null || form.getName().isBlank() || form.getName().strip().length() > 120 || (form.getDescription() != null && form.getDescription().length() > 1000))
            throw new IllegalArgumentException("Enter a habit name up to 120 characters and description up to 1,000 characters.");
        if (form.getFrequency() == null || form.getTargetCount() < 1 || form.getTargetCount() > 31) throw new IllegalArgumentException("Choose a valid habit frequency and target count.");
        if (form.getFrequency() == HabitFrequency.WEEKLY_TARGET && form.getTargetCount() > 7) throw new IllegalArgumentException("A weekly target must be 7 or fewer times.");
        if (form.getFrequency() == HabitFrequency.MONTHLY_TARGET && form.getTargetCount() > 31) throw new IllegalArgumentException("A monthly target must be 31 or fewer times.");
        if (form.getFrequency() == HabitFrequency.SELECTED_DAYS && parseDays(form.getSelectedWeekdays()).isEmpty()) throw new IllegalArgumentException("Select at least one weekday.");
        if (form.getFrequency() != HabitFrequency.SELECTED_DAYS && form.getSelectedWeekdays() != null && !form.getSelectedWeekdays().isBlank()) parseDays(form.getSelectedWeekdays());
        if (form.getTargetQuantity() != null && form.getTargetQuantity().signum() <= 0) throw new IllegalArgumentException("A quantity target must be greater than zero.");
        if (form.getTargetQuantity() != null && (form.getUnit() == null || form.getUnit().isBlank())) throw new IllegalArgumentException("Add the unit for the quantity target.");
        try { ZoneId.of(form.getTimezone()); } catch (Exception e) { throw new IllegalArgumentException("Choose a valid timezone."); }
        if (form.getLinkedGoalId() != null && goals.findByIdAndOwnerId(form.getLinkedGoalId(), ownerId).isEmpty()) throw new IllegalArgumentException("Choose one of your own goals.");
        if (form.getLinkedTaskId() != null && tasks.findDetailByIdAndOwner(form.getLinkedTaskId(), ownerId).isEmpty()) throw new IllegalArgumentException("Choose one of your own tasks.");
        if (creating && form.getStartDate() == null) throw new IllegalArgumentException("Choose a start date.");
        if (form.getUnit() != null && form.getUnit().length() > 40) throw new IllegalArgumentException("Use a unit up to 40 characters.");
    }

    private HabitSchedule schedule(UUID owner, UUID habit, LocalDate date, HabitForm form) {
        String days = form.getFrequency() == HabitFrequency.SELECTED_DAYS ? String.join(",", parseDays(form.getSelectedWeekdays()).stream().sorted().map(String::valueOf).toList()) : "";
        return HabitSchedule.create(owner, habit, date, form.getFrequency(), days, form.getTargetCount(), form.getTargetQuantity(), form.getUnit(), clock.instant());
    }
    private Habit ownHabit(UUID owner, UUID id) { return habits.findByIdAndOwnerId(id, owner).orElseThrow(() -> new IllegalArgumentException("That habit is not available.")); }
    private Habit ownLockedHabit(UUID owner, UUID id) { return habits.findLockedByIdAndOwnerId(id, owner).orElseThrow(() -> new IllegalArgumentException("That habit is not available.")); }
    private LocalDate today(Habit habit) { return LocalDate.now(clock.withZone(ZoneId.of(habit.getTimezoneId()))); }
    private HabitSchedule scheduleFor(UUID owner, UUID id, LocalDate date) { return schedules.findFirstByOwnerIdAndHabitIdAndEffectiveFromLessThanEqualOrderByEffectiveFromDesc(owner, id, date).orElse(null); }
    private HabitSchedule applicable(List<HabitSchedule> rules, LocalDate date) { HabitSchedule current = null; for (HabitSchedule rule : rules) { if (rule.getEffectiveFrom().isAfter(date)) break; current = rule; } return current; }
    private boolean isScheduled(HabitSchedule rule, LocalDate date) {
        return switch (rule.getFrequency()) {
            case DAILY -> true;
            case SELECTED_DAYS -> parseDays(rule.getSelectedWeekdays()).contains(date.getDayOfWeek().getValue());
            case WEEKLY_TARGET, MONTHLY_TARGET -> false;
        };
    }
    private Set<Integer> parseDays(String raw) {
        Set<Integer> days = new HashSet<>();
        try { if (raw != null) for (String part : raw.split(",")) { int day = Integer.parseInt(part.strip()); if (day < 1 || day > 7) throw new NumberFormatException(); days.add(day); } }
        catch (NumberFormatException e) { throw new IllegalArgumentException("Weekdays must be ISO numbers from 1 (Monday) to 7 (Sunday), separated by commas."); }
        return days;
    }
    private boolean isPaused(UUID owner, UUID id, LocalDate date) { return isPaused(pauses.findAllByOwnerIdAndHabitIdOrderByStartDateAsc(owner, id), date); }
    private boolean isPaused(List<HabitPause> rows, LocalDate date) { return rows.stream().anyMatch(p -> !date.isBefore(p.getStartDate()) && (p.getEndDate() == null || !date.isAfter(p.getEndDate()))); }
    private boolean overlaps(HabitPause pause, LocalDate start, LocalDate end) { return !pause.getStartDate().isAfter(end) && (pause.getEndDate() == null || !pause.getEndDate().isBefore(start)); }
    private LocalDate max(LocalDate value, LocalDate first, LocalDate second) { return value.isBefore(first) ? first : value.isBefore(second) ? second : value; }

    private static final class Period {
        private final LocalDate start; private final LocalDate end; private final HabitSchedule schedule;
        private int count; private BigDecimal total = BigDecimal.ZERO;
        private boolean successful; private boolean closed; private boolean paused;
        private Period(LocalDate start, LocalDate end, HabitSchedule schedule) { this.start = start; this.end = end; this.schedule = schedule; }
    }
}
