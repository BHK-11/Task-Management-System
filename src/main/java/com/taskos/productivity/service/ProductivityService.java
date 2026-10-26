package com.taskos.productivity.service;

import com.taskos.productivity.entity.*;
import com.taskos.productivity.repository.*;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.*;
import com.taskos.task.repository.TaskActivityRepository;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskNotFoundException;
import com.taskos.task.service.TaskRuleException;
import com.taskos.task.service.TaskService;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owns methodology state while routing all actual task changes through TaskService. */
@Service
@Transactional
public class ProductivityService {
    // shortcut: method views cap at 250 active tasks, paginate when users outgrow this limit.
    private static final int TASK_LIMIT = 250;
    private static final List<TaskStatus> ACTIVE = List.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS, TaskStatus.ON_HOLD);

    private final TaskRepository tasks;
    private final TaskProductivityRepository metadata;
    private final GtdInboxRepository inbox;
    private final DailyPlanRepository plans;
    private final DailyPlanEntryRepository entries;
    private final WeeklyReviewRepository reviews;
    private final ProductivityPreferenceRepository preferences;
    private final UserAccountRepository users;
    private final TaskActivityRepository activity;
    private final TaskService taskService;

    public ProductivityService(TaskRepository tasks, TaskProductivityRepository metadata, GtdInboxRepository inbox,
            DailyPlanRepository plans, DailyPlanEntryRepository entries, WeeklyReviewRepository reviews,
            ProductivityPreferenceRepository preferences, UserAccountRepository users,
            TaskActivityRepository activity, TaskService taskService) {
        this.tasks = tasks; this.metadata = metadata; this.inbox = inbox; this.plans = plans;
        this.entries = entries; this.reviews = reviews; this.preferences = preferences; this.users = users;
        this.activity = activity; this.taskService = taskService;
    }

    @Transactional(readOnly = true)
    public List<Task> activeTasks(UUID ownerId) {
        return tasks.findProductivityTasks(ownerId, ACTIVE, PageRequest.of(0, TASK_LIMIT));
    }

    @Transactional(readOnly = true)
    public Map<UUID, TaskProductivity> metadataFor(UUID ownerId, Collection<Task> selected) {
        if (selected.isEmpty()) return Map.of();
        List<UUID> ids = selected.stream().map(Task::getId).toList();
        Map<UUID, TaskProductivity> result = new HashMap<>();
        metadata.findAllByOwner_IdAndIdIn(ownerId, ids).forEach(item -> result.put(item.getId(), item));
        return result;
    }

    @Transactional(readOnly = true)
    public List<GtdInboxItem> inbox(UUID ownerId, InboxState state) {
        return inbox.findAllByOwner_IdAndStateOrderByCapturedAtAsc(ownerId, state);
    }

    public void reopenInboxCapture(UUID ownerId, UUID itemId) {
        GtdInboxItem item = inbox.findByIdAndOwner_Id(itemId, ownerId).orElseThrow(TaskNotFoundException::new);
        try { item.reopen(Instant.now()); }
        catch (IllegalStateException ex) { throw new TaskRuleException(ex.getMessage()); }
        inbox.save(item);
    }

    @Transactional(readOnly = true)
    public long openInboxCount(UUID ownerId) { return inbox.countByOwner_IdAndState(ownerId, InboxState.OPEN); }

    @Transactional(readOnly = true)
    public Optional<TaskProductivity> taskMetadata(UUID ownerId, UUID taskId) {
        return metadata.findByIdAndOwner_Id(taskId, ownerId);
    }

    @Transactional(readOnly = true)
    public Map<Quadrant, List<Task>> quadrants(UUID ownerId, List<Task> activeTasks) {
        Map<UUID, TaskProductivity> byTask = metadataFor(ownerId, activeTasks);
        Map<Quadrant, List<Task>> grouped = new EnumMap<>(Quadrant.class);
        for (Quadrant quadrant : Quadrant.values()) grouped.put(quadrant, new ArrayList<>());
        for (Task task : activeTasks) grouped.get(quadrant(byTask.get(task.getId()))).add(task);
        return grouped;
    }

    public void classifyEisenhower(UUID ownerId, UUID taskId, Quadrant quadrant) {
        if (quadrant == null) throw new TaskRuleException("Choose a quadrant.");
        Task task = activeTask(ownerId, taskId);
        TaskProductivity item = getOrCreate(task, ownerId);
        Boolean urgent = switch (quadrant) { case DO, DELEGATE -> true; case SCHEDULE, ELIMINATE -> false; case UNCLASSIFIED -> null; };
        Boolean important = switch (quadrant) { case DO, SCHEDULE -> true; case DELEGATE, ELIMINATE -> false; case UNCLASSIFIED -> null; };
        item.classifyEisenhower(urgent, important, Instant.now());
        metadata.save(item);
        record(ownerId, taskId, TaskActivityType.EISENHOWER_CLASSIFIED, "Eisenhower quadrant set to " + quadrant.name());
    }

    public UUID capture(UUID ownerId, String text) {
        if (text == null || text.isBlank() || text.trim().length() > 500) throw new TaskRuleException("Capture a note between 1 and 500 characters.");
        UserAccount owner = user(ownerId);
        Instant now = Instant.now();
        GtdInboxItem item = inbox.save(GtdInboxItem.capture(owner, text, now));
        record(ownerId, null, TaskActivityType.GTD_CAPTURED, "Item captured in the GTD inbox");
        return item.getId();
    }

    public UUID clarifyInbox(UUID ownerId, UUID itemId, InboxState outcome, TaskPriority priority) {
        GtdInboxItem item = inbox.findByIdAndOwner_Id(itemId, ownerId).orElseThrow(TaskNotFoundException::new);
        if (item.getState() != InboxState.OPEN) throw new TaskRuleException("This inbox item was already clarified.");
        if (outcome == null || outcome == InboxState.OPEN) throw new TaskRuleException("Choose what to do with this capture.");
        Task converted = null;
        if (outcome == InboxState.CONVERTED) {
            TaskForm form = new TaskForm();
            form.setTitle(item.getCaptureText());
            form.setDescription("");
            form.setStatus(TaskStatus.TODO);
            form.setPriority(priority == null ? TaskPriority.MEDIUM : priority);
            UUID taskId = taskService.create(ownerId, form);
            converted = tasks.findDetailByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
            getOrCreate(converted, ownerId).setGtd(GtdState.NEXT_ACTION, null, null, Instant.now());
            record(ownerId, taskId, TaskActivityType.GTD_CLARIFIED, "GTD inbox item converted to a next action");
        } else {
            record(ownerId, null, TaskActivityType.GTD_CLARIFIED, "GTD inbox item clarified as " + outcome.name());
        }
        try { item.clarify(outcome, converted, Instant.now()); }
        catch (IllegalStateException | IllegalArgumentException ex) { throw new TaskRuleException(ex.getMessage()); }
        inbox.save(item);
        return converted == null ? null : converted.getId();
    }

    public UUID clarifyInbox(UUID ownerId, UUID itemId, InboxState outcome, TaskPriority priority, String taskTitle) {
        GtdInboxItem item = inbox.findByIdAndOwner_Id(itemId, ownerId).orElseThrow(TaskNotFoundException::new);
        if (item.getState() != InboxState.OPEN) throw new TaskRuleException("This inbox item was already clarified.");
        if (outcome != InboxState.CONVERTED) return clarifyInbox(ownerId, itemId, outcome, priority);
        if (taskTitle == null || taskTitle.isBlank() || taskTitle.trim().length() > 160) {
            throw new TaskRuleException("Choose a task title between 1 and 160 characters before converting this capture.");
        }
        TaskForm form = new TaskForm();
        form.setTitle(taskTitle.trim()); form.setDescription("Captured from GTD inbox: " + item.getCaptureText());
        form.setStatus(TaskStatus.TODO); form.setPriority(priority == null ? TaskPriority.MEDIUM : priority);
        UUID taskId = taskService.create(ownerId, form);
        Task converted = tasks.findDetailByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
        getOrCreate(converted, ownerId).setGtd(GtdState.NEXT_ACTION, null, null, Instant.now());
        item.clarify(InboxState.CONVERTED, converted, Instant.now());
        inbox.save(item);
        record(ownerId, taskId, TaskActivityType.GTD_CLARIFIED, "GTD inbox item converted to a next action");
        return taskId;
    }

    public void setGtdState(UUID ownerId, UUID taskId, GtdState state, String waitingFor, LocalDate followUpDate) {
        if (state == null) throw new TaskRuleException("Choose a GTD list.");
        if (state == GtdState.WAITING_FOR && (waitingFor == null || waitingFor.isBlank() || waitingFor.trim().length() > 160)) {
            throw new TaskRuleException("Add who or what you are waiting for (up to 160 characters).");
        }
        Task task = activeTask(ownerId, taskId);
        getOrCreate(task, ownerId).setGtd(state, waitingFor, followUpDate, Instant.now());
        record(ownerId, taskId, TaskActivityType.GTD_CLARIFIED, "GTD state set to " + state.name());
    }

    public void setAbc(UUID ownerId, UUID taskId, AbcCategory category) {
        lockUser(ownerId);
        Task task = activeTask(ownerId, taskId);
        TaskProductivity item = getOrCreate(task, ownerId);
        Integer rank = category == null ? null : item.getAbcCategory() == category && item.getAbcRank() != null
                ? item.getAbcRank() : nextAbcRank(ownerId, category);
        item.setAbc(category, rank == null ? 0 : rank, Instant.now());
        record(ownerId, taskId, TaskActivityType.ABC_ASSIGNED, category == null ? "ABC category cleared" : "ABC category set to " + category.name());
    }

    public void moveAbc(UUID ownerId, UUID taskId, int direction) {
        lockUser(ownerId);
        if (direction != -1 && direction != 1) throw new TaskRuleException("Choose a valid ranking direction.");
        Task task = activeTask(ownerId, taskId);
        TaskProductivity item = metadata.findByIdAndOwner_Id(taskId, ownerId).orElseThrow(() -> new TaskRuleException("Assign an ABC category before reordering."));
        if (item.getAbcCategory() == null || item.getAbcRank() == null) throw new TaskRuleException("Assign an ABC category before reordering.");
        List<TaskProductivity> same = metadata.findAllByOwner_Id(ownerId).stream()
                .filter(candidate -> candidate.getAbcCategory() == item.getAbcCategory())
                .sorted(Comparator.comparing(TaskProductivity::getAbcRank, Comparator.nullsLast(Integer::compareTo)))
                .toList();
        int index = same.indexOf(item), adjacent = index + direction;
        if (adjacent < 0 || adjacent >= same.size()) return;
        TaskProductivity other = same.get(adjacent);
        List<TaskProductivity> reordered = new ArrayList<>(same);
        Collections.swap(reordered, index, adjacent);
        int offset = same.stream().mapToInt(TaskProductivity::getAbcRank).max().orElse(0) + same.size() + 1;
        for (int i = 0; i < reordered.size(); i++) reordered.get(i).setAbc(item.getAbcCategory(), offset + i, Instant.now());
        metadata.saveAllAndFlush(reordered);
        for (int i = 0; i < reordered.size(); i++) reordered.get(i).setAbc(item.getAbcCategory(), i, Instant.now());
        metadata.saveAllAndFlush(reordered);
    }

    private int nextAbcRank(UUID ownerId, AbcCategory category) {
        return metadata.findAllByOwner_Id(ownerId).stream()
                .filter(item -> item.getAbcCategory() == category && item.getAbcRank() != null)
                .mapToInt(TaskProductivity::getAbcRank).max().orElse(-1) + 1;
    }

    public void setMoscow(UUID ownerId, UUID taskId, MoscowCategory category) {
        if (category == null) throw new TaskRuleException("Choose a MoSCoW category.");
        Task task = activeTask(ownerId, taskId);
        getOrCreate(task, ownerId).setMoscow(category, Instant.now());
        record(ownerId, taskId, TaskActivityType.MOSCOW_ASSIGNED, "MoSCoW category set to " + category.name());
    }

    public void assessImpact(UUID ownerId, UUID taskId, Integer impact, Integer effort, String note) {
        Task task = activeTask(ownerId, taskId);
        if (note != null && note.trim().length() > 500) throw new TaskRuleException("The assessment note must be 500 characters or fewer.");
        try { getOrCreate(task, ownerId).assessImpact(impact, effort, note, Instant.now()); }
        catch (IllegalArgumentException ex) { throw new TaskRuleException(ex.getMessage()); }
        record(ownerId, taskId, TaskActivityType.IMPACT_ASSESSED, impact == null ? "Impact and effort scores cleared" : "Impact and effort assessed by user");
    }

    @Transactional(readOnly = true)
    public List<Task> paretoRanked(UUID ownerId, List<Task> activeTasks) {
        Map<UUID, TaskProductivity> byTask = metadataFor(ownerId, activeTasks);
        return activeTasks.stream().filter(task -> byTask.get(task.getId()) != null
                        && byTask.get(task.getId()).getImpactScore() != null && byTask.get(task.getId()).getEffortScore() != null)
                .sorted(Comparator.<Task>comparingDouble(task -> ratio(byTask.get(task.getId()))).reversed()
                        .thenComparing(Task::getUpdatedAt).thenComparing(Task::getId))
                .toList();
    }

    public ProductivityPreference preference(UUID ownerId) {
        return preferences.findById(ownerId).orElseGet(() -> preferences.save(ProductivityPreference.create(ownerId, Instant.now())));
    }

    public void updatePreference(UUID ownerId, ProductivityMethod method, DailyPlanMode mode) {
        if (method == null || mode == null) throw new TaskRuleException("Choose both a primary method and a default daily plan.");
        ProductivityPreference preference = preference(ownerId);
        preference.update(method, mode, Instant.now());
        preferences.save(preference);
    }

    public DailyPlan dailyPlan(UUID ownerId, LocalDate date) {
        UserAccount owner = user(ownerId);
        return plans.findByOwner_IdAndPlanningDate(ownerId, date).orElseGet(() -> {
            ProductivityPreference preference = preference(ownerId);
            return plans.save(DailyPlan.create(owner, date, preference.getDefaultPlanMode()));
        });
    }

    public List<DailyPlanEntry> planEntries(UUID ownerId, LocalDate date) {
        DailyPlan plan = dailyPlan(ownerId, date);
        List<DailyPlanEntry> found = entries.findAllByPlan_IdOrderByPositionAsc(plan.getId());
        List<DailyPlanEntry> active = new ArrayList<>();
        boolean changed = false;
        for (DailyPlanEntry entry : found) {
            if (entry.getTask().getArchivedAt() != null) { entries.delete(entry); changed = true; }
            else active.add(entry);
        }
        if (changed) entries.flush();
        if (plan.getFrogTask() != null && plan.getFrogTask().getArchivedAt() != null) {
            plan.selectFrog(null); plans.save(plan);
        }
        return active;
    }

    public void addToPlan(UUID ownerId, LocalDate date, UUID taskId, PlanBucket bucket) {
        UserAccount owner = lockUser(ownerId);
        DailyPlan plan = dailyPlan(ownerId, date);
        List<DailyPlanEntry> current = planEntries(ownerId, date);
        if (current.stream().anyMatch(entry -> entry.getTask().getId().equals(taskId))) throw new TaskRuleException("That task is already in this day's plan.");
        Task task = activeTask(ownerId, taskId);
        validateBucket(plan.getMode(), bucket);
        List<DailyPlanEntry> inBucket = current.stream().filter(entry -> entry.getBucket() == bucket).toList();
        if (inBucket.size() >= capacity(plan.getMode(), bucket)) throw new TaskRuleException(capacityMessage(plan.getMode(), bucket));
        DailyPlanEntry entry = DailyPlanEntry.create(plan, owner, task, bucket, inBucket.size());
        entries.save(entry);
        record(ownerId, taskId, TaskActivityType.DAILY_PLAN_UPDATED, "Task added to daily plan for " + date);
    }

    public UUID createAndPlan(UUID ownerId, LocalDate date, String title, TaskPriority priority, PlanBucket bucket) {
        lockUser(ownerId);
        if (title == null || title.isBlank() || title.trim().length() > 160)
            throw new TaskRuleException("Enter a task title between 1 and 160 characters.");
        TaskForm form = new TaskForm();
        form.setTitle(title.trim()); form.setStatus(TaskStatus.TODO);
        form.setPriority(priority == null ? TaskPriority.MEDIUM : priority);
        UUID taskId = taskService.create(ownerId, form);
        addToPlan(ownerId, date, taskId, bucket);
        return taskId;
    }

    public void removeFromPlan(UUID ownerId, LocalDate date, UUID taskId) {
        lockUser(ownerId);
        DailyPlan plan = dailyPlan(ownerId, date);
        List<DailyPlanEntry> current = planEntries(ownerId, date);
        DailyPlanEntry selected = current.stream().filter(entry -> entry.getTask().getId().equals(taskId)).findFirst().orElseThrow(TaskNotFoundException::new);
        PlanBucket bucket = selected.getBucket();
        entries.delete(selected);
        entries.flush();
        compact(current.stream().filter(entry -> entry != selected && entry.getBucket() == bucket).toList());
        record(ownerId, taskId, TaskActivityType.DAILY_PLAN_UPDATED, "Task removed from daily plan for " + date);
    }

    public void moveInPlan(UUID ownerId, LocalDate date, UUID taskId, int direction) {
        lockUser(ownerId);
        if (direction != -1 && direction != 1) throw new TaskRuleException("Choose a valid move direction.");
        List<DailyPlanEntry> list = planEntries(ownerId, date);
        DailyPlanEntry selected = list.stream().filter(entry -> entry.getTask().getId().equals(taskId)).findFirst().orElseThrow(TaskNotFoundException::new);
        List<DailyPlanEntry> same = list.stream().filter(entry -> entry.getBucket() == selected.getBucket())
                .sorted(Comparator.comparingInt(DailyPlanEntry::getPosition)).toList();
        int index = same.indexOf(selected), next = index + direction;
        if (next < 0 || next >= same.size()) return;
        List<DailyPlanEntry> reordered = new ArrayList<>(same);
        Collections.swap(reordered, index, next);
        compact(reordered);
    }

    private void compact(List<DailyPlanEntry> ordered) {
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).move(ordered.get(i).getBucket(), 1000 + i);
        entries.saveAllAndFlush(ordered);
        for (int i = 0; i < ordered.size(); i++) ordered.get(i).move(ordered.get(i).getBucket(), i);
        entries.saveAllAndFlush(ordered);
    }

    public void selectFrog(UUID ownerId, LocalDate date, UUID taskId) {
        lockUser(ownerId);
        Task task = taskId == null ? null : activeTask(ownerId, taskId);
        DailyPlan plan = dailyPlan(ownerId, date);
        UUID previous = plan.getFrogTask() == null ? null : plan.getFrogTask().getId();
        plan.selectFrog(task);
        plans.save(plan);
        if (task != null && !task.getId().equals(previous)) record(ownerId, taskId, TaskActivityType.FROG_SELECTED, "Eat the Frog task selected for " + date);
    }

    public List<Task> frogCandidates(UUID ownerId, List<Task> activeTasks, LocalDate today) {
        return activeTasks.stream().sorted(Comparator
                .comparing((Task task) -> task.getPriority() == TaskPriority.URGENT ? 0 : task.getPriority() == TaskPriority.HIGH ? 1 : task.getPriority() == TaskPriority.MEDIUM ? 2 : 3)
                .thenComparing(task -> task.getDueDate() == null ? LocalDate.MAX : task.getDueDate())
                .thenComparing(Task::getUpdatedAt).thenComparing(Task::getId)).limit(20).toList();
    }

    public void switchPlanMode(UUID ownerId, LocalDate date, DailyPlanMode nextMode, boolean confirmed, List<PlanBucket> assignments) {
        lockUser(ownerId);
        if (nextMode == null) throw new TaskRuleException("Choose a planning method.");
        DailyPlan plan = dailyPlan(ownerId, date);
        if (plan.getMode() == nextMode) return;
        if (!confirmed) throw new TaskRuleException("Confirm how the current plan should be preserved before switching methods.");
        List<DailyPlanEntry> current = planEntries(ownerId, date);
        List<DailyPlanEntry> ordered;
        if (nextMode == DailyPlanMode.IVY_LEE) {
            if (current.size() > 6) throw new TaskRuleException("This plan has more than six tasks. Remove tasks before converting it to Ivy Lee.");
            ordered = current.stream().sorted(Comparator.comparing((DailyPlanEntry entry) -> entry.getBucket().ordinal())
                    .thenComparingInt(DailyPlanEntry::getPosition)).toList();
            for (int i = 0; i < ordered.size(); i++) ordered.get(i).move(PlanBucket.IVY_LEE, 1000 + i);
        } else {
            if (assignments == null || assignments.size() != current.size()) throw new TaskRuleException("Assign a 1-3-5 size to every existing task before switching.");
            Map<UUID, PlanBucket> chosen = new LinkedHashMap<>();
            List<DailyPlanEntry> ivy = current.stream().sorted(Comparator.comparingInt(DailyPlanEntry::getPosition)).toList();
            for (int i = 0; i < ivy.size(); i++) {
                PlanBucket bucket = assignments.get(i);
                if (bucket == null || !Set.of(PlanBucket.BIG, PlanBucket.MEDIUM, PlanBucket.SMALL).contains(bucket)) {
                    throw new TaskRuleException("Choose Big, Medium or Small for each current task.");
                }
                chosen.put(ivy.get(i).getTask().getId(), bucket);
            }
            if (chosen.values().stream().filter(b -> b == PlanBucket.BIG).count() > 1
                    || chosen.values().stream().filter(b -> b == PlanBucket.MEDIUM).count() > 3
                    || chosen.values().stream().filter(b -> b == PlanBucket.SMALL).count() > 5) {
                throw new TaskRuleException("The conversion exceeds a 1-3-5 group capacity.");
            }
            Map<PlanBucket, Integer> positions = new EnumMap<>(PlanBucket.class);
            ordered = ivy;
            for (DailyPlanEntry entry : ordered) {
                PlanBucket bucket = chosen.get(entry.getTask().getId());
                entry.move(bucket, 1000 + positions.merge(bucket, 1, Integer::sum) - 1);
            }
        }
        entries.saveAllAndFlush(ordered);
        Map<PlanBucket, Integer> ranks = new EnumMap<>(PlanBucket.class);
        for (DailyPlanEntry entry : ordered) entry.move(entry.getBucket(), ranks.merge(entry.getBucket(), 1, Integer::sum) - 1);
        entries.saveAllAndFlush(ordered);
        plan.changeMode(nextMode);
        plans.save(plan);
        record(ownerId, null, TaskActivityType.DAILY_PLAN_UPDATED, "Daily plan method changed to " + nextMode.name() + " for " + date);
    }

    public WeeklyReview weeklyReview(UUID ownerId, LocalDate date) {
        LocalDate weekStart = date.with(TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY));
        return reviews.findByOwner_IdAndWeekStart(ownerId, weekStart).orElseGet(() -> reviews.save(WeeklyReview.start(user(ownerId), weekStart)));
    }

    public void saveWeeklyReview(UUID ownerId, LocalDate weekStart, boolean inboxProcessed, boolean next,
            boolean waiting, boolean someday, boolean commitments) {
        if (weekStart == null || weekStart.getDayOfWeek() != java.time.DayOfWeek.MONDAY) throw new TaskRuleException("Choose a week starting on Monday.");
        WeeklyReview review = reviews.findByOwner_IdAndWeekStart(ownerId, weekStart)
                .orElseGet(() -> WeeklyReview.start(user(ownerId), weekStart));
        boolean wasComplete = review.getCompletedAt() != null;
        review.update(inboxProcessed, next, waiting, someday, commitments, Instant.now());
        reviews.save(review);
        if (!wasComplete && review.getCompletedAt() != null) record(ownerId, null, TaskActivityType.WEEKLY_REVIEW_COMPLETED, "Weekly review completed for week of " + weekStart);
    }

    public LocalDate today(UUID ownerId) { return LocalDate.now(ZoneId.of(user(ownerId).getTimezone())); }

    public double ratio(TaskProductivity item) {
        return item.getImpactScore() == null || item.getEffortScore() == null ? 0d
                : item.getImpactScore().doubleValue() / item.getEffortScore().doubleValue();
    }

    public String priorityRecommendation(UUID ownerId, List<Task> active, LocalDate today) {
        Map<UUID, TaskProductivity> data = metadataFor(ownerId, active);
        Optional<Task> assessed = active.stream().filter(task -> data.get(task.getId()) != null && data.get(task.getId()).getImpactScore() != null)
                .max(Comparator.<Task>comparingDouble(task -> ratio(data.get(task.getId()))).thenComparing(Task::getUpdatedAt, Comparator.reverseOrder()));
        if (assessed.isPresent()) {
            Task task = assessed.get(); TaskProductivity item = data.get(task.getId());
            return "Consider “" + task.getTitle() + "”: your impact score is " + item.getImpactScore() + " and effort score is "
                    + item.getEffortScore() + " (impact ÷ effort = " + String.format(Locale.ROOT, "%.2f", ratio(item)) + ").";
        }
        Optional<Task> dueSoon = active.stream().filter(task -> task.getDueDate() != null && !task.getDueDate().isAfter(today.plusDays(1))
                        && (task.getPriority() == TaskPriority.HIGH || task.getPriority() == TaskPriority.URGENT))
                .min(Comparator.comparing(Task::getDueDate).thenComparing(Task::getId));
        return dueSoon.map(task -> "Consider “" + task.getTitle() + "”: it has " + task.getPriority().name()
                + " priority and is due " + task.getDueDate() + ".")
                .orElse("Not enough information for a recommendation.");
    }

    private Quadrant quadrant(TaskProductivity item) {
        if (item == null || item.getUrgent() == null || item.getImportant() == null) return Quadrant.UNCLASSIFIED;
        if (item.getUrgent() && item.getImportant()) return Quadrant.DO;
        if (!item.getUrgent() && item.getImportant()) return Quadrant.SCHEDULE;
        if (item.getUrgent()) return Quadrant.DELEGATE;
        return Quadrant.ELIMINATE;
    }

    private void validateBucket(DailyPlanMode mode, PlanBucket bucket) {
        if (bucket == null || (mode == DailyPlanMode.IVY_LEE && bucket != PlanBucket.IVY_LEE)
                || (mode == DailyPlanMode.ONE_THREE_FIVE && !Set.of(PlanBucket.BIG, PlanBucket.MEDIUM, PlanBucket.SMALL).contains(bucket))) {
            throw new TaskRuleException("Choose a group that belongs to the selected daily method.");
        }
    }

    private int capacity(DailyPlanMode mode, PlanBucket bucket) {
        if (mode == DailyPlanMode.IVY_LEE) return 6;
        return switch (bucket) { case BIG -> 1; case MEDIUM -> 3; case SMALL -> 5; case IVY_LEE -> 0; };
    }

    private String capacityMessage(DailyPlanMode mode, PlanBucket bucket) {
        return mode == DailyPlanMode.IVY_LEE ? "The Ivy Lee plan allows up to six tasks."
                : "The " + bucket.name().toLowerCase(Locale.ROOT) + " group has reached its 1-3-5 capacity.";
    }

    private TaskProductivity getOrCreate(Task task, UUID ownerId) {
        return metadata.findByIdAndOwner_Id(task.getId(), ownerId).orElseGet(() ->
                metadata.save(TaskProductivity.create(task, task.getOwner(), Instant.now())));
    }

    private Task activeTask(UUID ownerId, UUID taskId) {
        Task task = tasks.findDetailByIdAndOwner(taskId, ownerId).orElseThrow(TaskNotFoundException::new);
        if (task.getParentId() != null) throw new TaskRuleException("Productivity methods are available for main tasks.");
        if (task.getArchivedAt() != null || !ACTIVE.contains(task.getStatus())) throw new TaskRuleException("Restore or reopen this task before changing its productivity data.");
        return task;
    }

    private UserAccount user(UUID ownerId) { return users.findById(ownerId).orElseThrow(TaskNotFoundException::new); }
    private UserAccount lockUser(UUID ownerId) { return users.findLockedById(ownerId).orElseThrow(TaskNotFoundException::new); }

    private void record(UUID ownerId, UUID taskId, TaskActivityType type, String summary) {
        UserAccount owner = user(ownerId);
        activity.save(TaskActivity.create(taskId, owner, owner, type, summary, Instant.now()));
    }
}
