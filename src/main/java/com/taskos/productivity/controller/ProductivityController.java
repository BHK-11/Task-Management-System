package com.taskos.productivity.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.productivity.entity.*;
import com.taskos.productivity.service.ProductivityService;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.Task;
import com.taskos.task.entity.TaskPriority;
import com.taskos.task.service.TaskRuleException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProductivityController {
    private final ProductivityService productivity;

    public ProductivityController(ProductivityService productivity) { this.productivity = productivity; }

    @GetMapping("/productivity")
    public String hub(Authentication auth, Model model) {
        UUID owner = owner(auth);
        List<Task> tasks = productivity.activeTasks(owner);
        Map<UUID, TaskProductivity> metadata = productivity.metadataFor(owner, tasks);
        model.addAttribute("kind", "hub");
        model.addAttribute("taskCount", tasks.size());
        model.addAttribute("unclassifiedCount", productivity.quadrants(owner, tasks).get(Quadrant.UNCLASSIFIED).size());
        model.addAttribute("inboxCount", productivity.inbox(owner, InboxState.OPEN).size());
        model.addAttribute("today", productivity.today(owner));
        model.addAttribute("plan", productivity.dailyPlan(owner, productivity.today(owner)));
        model.addAttribute("planEntries", productivity.planEntries(owner, productivity.today(owner)));
        model.addAttribute("preference", productivity.preference(owner));
        model.addAttribute("methodOptions", ProductivityMethod.values());
        model.addAttribute("defaultPlanOptions", DailyPlanMode.values());
        model.addAttribute("highImpactCount", productivity.paretoRanked(owner, tasks).size());
        model.addAttribute("recommendation", productivity.priorityRecommendation(owner, tasks, productivity.today(owner)));
        return page(model, "Productivity methods", "One task list, several ways to decide what deserves attention.");
    }

    @GetMapping("/productivity/eisenhower")
    public String eisenhower(Authentication auth, Model model) {
        UUID owner = owner(auth); List<Task> tasks = productivity.activeTasks(owner);
        model.addAttribute("kind", "eisenhower");
        Map<Quadrant, List<Task>> groups = productivity.quadrants(owner, tasks);
        model.addAttribute("quadrants", groups);
        model.addAttribute("doTasks", groups.get(Quadrant.DO));
        model.addAttribute("scheduleTasks", groups.get(Quadrant.SCHEDULE));
        model.addAttribute("delegateTasks", groups.get(Quadrant.DELEGATE));
        model.addAttribute("eliminateTasks", groups.get(Quadrant.ELIMINATE));
        model.addAttribute("unclassifiedTasks", groups.get(Quadrant.UNCLASSIFIED));
        model.addAttribute("quadrantOptions", Quadrant.values());
        return page(model, "Eisenhower Matrix", "Classify urgency and importance independently. Unclassified tasks stay visible.");
    }

    @GetMapping("/productivity/gtd")
    public String gtd(@RequestParam(defaultValue = "INBOX") String list, Authentication auth, Model model) {
        UUID owner = owner(auth); List<Task> tasks = productivity.activeTasks(owner);
        GtdState selected = parseGtd(list);
        Map<UUID, TaskProductivity> taskMetadata = productivity.metadataFor(owner, tasks);
        model.addAttribute("kind", "gtd"); model.addAttribute("gtdTab", list);
        model.addAttribute("inboxItems", productivity.inbox(owner, InboxState.OPEN));
        model.addAttribute("resolvedCaptures", switch (list) {
            case "SOMEDAY" -> productivity.inbox(owner, InboxState.SOMEDAY);
            case "REFERENCE" -> productivity.inbox(owner, InboxState.REFERENCE);
            default -> List.of();
        });
        model.addAttribute("taskMetadata", taskMetadata);
        model.addAttribute("gtdTasks", selected == null ? List.of() : tasks.stream()
                .filter(task -> taskMetadata.get(task.getId()) != null
                        && taskMetadata.get(task.getId()).getGtdState() == selected).toList());
        model.addAttribute("selectedGtd", selected);
        model.addAttribute("gtdStates", GtdState.values()); model.addAttribute("inboxOutcomes", InboxState.values());
        model.addAttribute("priorities", TaskPriority.values());
        return page(model, "GTD workspace", "Capture quickly, then clarify each item into one next step or a deliberate holding place.");
    }

    @GetMapping("/productivity/abc")
    public String abc(Authentication auth, Model model) {
        UUID owner = owner(auth); List<Task> tasks = productivity.activeTasks(owner);
        Map<UUID, TaskProductivity> metadata = productivity.metadataFor(owner, tasks);
        model.addAttribute("kind", "abc"); model.addAttribute("abcCategories", AbcCategory.values());
        model.addAttribute("unclassifiedTasks", tasks.stream().filter(task -> metadata.get(task.getId()) == null
                || metadata.get(task.getId()).getAbcCategory() == null).toList());
        model.addAttribute("abcTasks", tasks.stream().filter(task -> metadata.get(task.getId()) != null
                && metadata.get(task.getId()).getAbcCategory() != null).sorted((left, right) -> {
                    TaskProductivity a = metadata.get(left.getId()), b = metadata.get(right.getId());
                    int category = a.getAbcCategory().compareTo(b.getAbcCategory());
                    return category != 0 ? category : Integer.compare(a.getAbcRank(), b.getAbcRank());
                }).toList());
        model.addAttribute("taskMetadata", metadata);
        return page(model, "ABC prioritization", "Assign A, B or C by your own judgment. Keep ranking visible inside each category.");
    }

    @GetMapping("/productivity/moscow")
    public String moscow(Authentication auth, Model model) {
        UUID owner = owner(auth); List<Task> tasks = productivity.activeTasks(owner);
        Map<UUID, TaskProductivity> metadata = productivity.metadataFor(owner, tasks);
        model.addAttribute("kind", "moscow"); model.addAttribute("moscowCategories", MoscowCategory.values());
        model.addAttribute("tasks", tasks); model.addAttribute("taskMetadata", metadata);
        return page(model, "MoSCoW priorities", "Mark Must, Should, Could or Won’t Have for the scope you are planning.");
    }

    @GetMapping("/productivity/pareto")
    public String pareto(Authentication auth, Model model) {
        UUID owner = owner(auth); List<Task> tasks = productivity.activeTasks(owner);
        List<Task> ranked = productivity.paretoRanked(owner, tasks);
        model.addAttribute("kind", "pareto"); model.addAttribute("tasks", ranked);
        model.addAttribute("taskMetadata", productivity.metadataFor(owner, ranked));
        model.addAttribute("unscoredTasks", tasks.stream().filter(task -> !ranked.contains(task)).toList());
        return page(model, "Impact and effort", "Record your own 1–5 scores. The list ranks impact ÷ effort; it does not claim an 80/20 result.");
    }

    @GetMapping("/productivity/daily")
    public String daily(@RequestParam(required = false) LocalDate date, Authentication auth, Model model) {
        UUID owner = owner(auth); LocalDate selected = date == null ? productivity.today(owner) : date;
        DailyPlan plan = productivity.dailyPlan(owner, selected);
        List<DailyPlanEntry> planEntries = productivity.planEntries(owner, selected);
        Map<String, List<DailyPlanEntry>> grouped = new java.util.LinkedHashMap<>();
        for (PlanBucket bucket : PlanBucket.values()) grouped.put(bucket.name(),
                planEntries.stream().filter(entry -> entry.getBucket() == bucket).toList());
        List<Task> active = productivity.activeTasks(owner);
        model.addAttribute("kind", "daily"); model.addAttribute("selectedDate", selected);
        model.addAttribute("plan", plan); model.addAttribute("planEntries", planEntries);
        model.addAttribute("planGroups", grouped);
        model.addAttribute("availableTasks", active.stream().filter(task -> planEntries.stream().noneMatch(entry -> entry.getTask().getId().equals(task.getId()))).toList());
        model.addAttribute("frogCandidates", productivity.frogCandidates(owner, active, productivity.today(owner)));
        model.addAttribute("planBuckets", plan.getMode() == DailyPlanMode.IVY_LEE ? List.of(PlanBucket.IVY_LEE) : List.of(PlanBucket.BIG, PlanBucket.MEDIUM, PlanBucket.SMALL));
        model.addAttribute("planModes", DailyPlanMode.values());
        model.addAttribute("ivyLeeModeName", DailyPlanMode.IVY_LEE.name());
        model.addAttribute("ivyLeeBucketName", PlanBucket.IVY_LEE.name());
        model.addAttribute("bigBucketName", PlanBucket.BIG.name());
        model.addAttribute("mediumBucketName", PlanBucket.MEDIUM.name());
        model.addAttribute("smallBucketName", PlanBucket.SMALL.name());
        model.addAttribute("assignmentBuckets", List.of(PlanBucket.BIG, PlanBucket.MEDIUM, PlanBucket.SMALL));
        return page(model, "Daily Focus", "Plan a finite list for this calendar date. Due dates and task priority remain separate.");
    }

    @GetMapping("/productivity/weekly-review")
    public String weekly(@RequestParam(required = false) LocalDate weekStart, Authentication auth, Model model) {
        UUID owner = owner(auth); LocalDate selected = weekStart == null ? productivity.today(owner) : weekStart;
        model.addAttribute("kind", "weekly"); model.addAttribute("review", productivity.weeklyReview(owner, selected));
        return page(model, "GTD weekly review", "Review the inbox, action lists and commitments. Completion requires every section.");
    }

    @PostMapping("/productivity/preferences")
    public String preferences(@RequestParam ProductivityMethod method, @RequestParam DailyPlanMode mode,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> productivity.updatePreference(owner(auth), method, mode), "/productivity", "Preferences saved.");
    }

    @PostMapping("/productivity/eisenhower/{taskId}")
    public String classify(@PathVariable UUID taskId, @RequestParam Quadrant quadrant, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> productivity.classifyEisenhower(owner(auth), taskId, quadrant), "/productivity/eisenhower", "Eisenhower category saved.");
    }

    @PostMapping("/productivity/gtd/capture")
    public String capture(@RequestParam String text, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> productivity.capture(owner(auth), text), "/productivity/gtd", "Captured in your inbox.");
    }

    @PostMapping("/productivity/gtd/inbox/{itemId}")
    public String clarify(@PathVariable UUID itemId, @RequestParam InboxState outcome,
            @RequestParam(required = false) TaskPriority priority, @RequestParam(required = false) String taskTitle,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> productivity.clarifyInbox(owner(auth), itemId, outcome, priority, taskTitle), "/productivity/gtd", "Inbox item clarified.");
    }

    @PostMapping("/productivity/gtd/inbox/{itemId}/reopen")
    public String reopenCapture(@PathVariable UUID itemId, Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> productivity.reopenInboxCapture(owner(auth), itemId),
                "/productivity/gtd", "Capture returned to the inbox.");
    }

    @PostMapping("/productivity/gtd/{taskId}")
    public String gtdState(@PathVariable UUID taskId, @RequestParam GtdState state,
            @RequestParam(required = false) String waitingFor, @RequestParam(required = false) LocalDate followUpDate,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> productivity.setGtdState(owner(auth), taskId, state, waitingFor, followUpDate), "/productivity/gtd?list=" + state.name(), "GTD list updated.");
    }

    @PostMapping("/productivity/abc/{taskId}")
    public String abc(@PathVariable UUID taskId, @RequestParam(required = false) AbcCategory category,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> productivity.setAbc(owner(auth), taskId, category), "/productivity/abc", "ABC category saved.");
    }

    @PostMapping("/productivity/abc/{taskId}/move")
    public String abcMove(@PathVariable UUID taskId, @RequestParam int direction,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> productivity.moveAbc(owner(auth), taskId, direction), "/productivity/abc", "ABC order updated.");
    }

    @PostMapping("/productivity/moscow/{taskId}")
    public String moscow(@PathVariable UUID taskId, @RequestParam MoscowCategory category,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> productivity.setMoscow(owner(auth), taskId, category), "/productivity/moscow", "MoSCoW category saved.");
    }

    @PostMapping("/productivity/pareto/{taskId}")
    public String assess(@PathVariable UUID taskId, @RequestParam(required = false) Integer impact,
            @RequestParam(required = false) Integer effort, @RequestParam(required = false) String note,
            Authentication auth, RedirectAttributes redirect) {
        return action(redirect, () -> productivity.assessImpact(owner(auth), taskId, impact, effort, note), "/productivity/pareto", "Assessment saved.");
    }

    @PostMapping("/productivity/daily/add")
    public String addPlan(@RequestParam LocalDate date, @RequestParam UUID taskId, @RequestParam PlanBucket bucket,
            Authentication auth, RedirectAttributes redirect) {
        String target = "/productivity/daily?date=" + date;
        return action(redirect, () -> productivity.addToPlan(owner(auth), date, taskId, bucket), target, "Task added to this day’s plan.");
    }

    @PostMapping("/productivity/daily/create")
    public String createPlanTask(@RequestParam LocalDate date, @RequestParam String title,
            @RequestParam(required = false) TaskPriority priority, @RequestParam PlanBucket bucket,
            Authentication auth, RedirectAttributes redirect) {
        String target = "/productivity/daily?date=" + date;
        return action(redirect, () -> productivity.createAndPlan(owner(auth), date, title, priority, bucket),
                target, "Task created and added to this day’s plan.");
    }

    @PostMapping("/productivity/daily/remove")
    public String removePlan(@RequestParam LocalDate date, @RequestParam UUID taskId,
            Authentication auth, RedirectAttributes redirect) {
        String target = "/productivity/daily?date=" + date;
        return action(redirect, () -> productivity.removeFromPlan(owner(auth), date, taskId), target, "Task removed from this day’s plan.");
    }

    @PostMapping("/productivity/daily/move")
    public String movePlan(@RequestParam LocalDate date, @RequestParam UUID taskId, @RequestParam int direction,
            Authentication auth, RedirectAttributes redirect) {
        String target = "/productivity/daily?date=" + date;
        return action(redirect, () -> productivity.moveInPlan(owner(auth), date, taskId, direction), target, "Plan order updated.");
    }

    @PostMapping("/productivity/daily/frog")
    public String frog(@RequestParam LocalDate date, @RequestParam(required = false) UUID taskId,
            Authentication auth, RedirectAttributes redirect) {
        String target = "/productivity/daily?date=" + date;
        return action(redirect, () -> productivity.selectFrog(owner(auth), date, taskId), target, "Eat the Frog selection saved.");
    }

    @PostMapping("/productivity/daily/mode")
    public String mode(@RequestParam LocalDate date, @RequestParam DailyPlanMode nextMode,
            @RequestParam(defaultValue = "false") boolean confirmed,
            @RequestParam(required = false) List<PlanBucket> assignments,
            Authentication auth, RedirectAttributes redirect) {
        String target = "/productivity/daily?date=" + date;
        return action(redirect, () -> productivity.switchPlanMode(owner(auth), date, nextMode, confirmed, assignments), target, "Daily method changed with your current tasks preserved.");
    }

    @PostMapping("/productivity/weekly-review")
    public String weeklySave(@RequestParam LocalDate weekStart,
            @RequestParam(defaultValue = "false") boolean inboxProcessed,
            @RequestParam(defaultValue = "false") boolean nextActionsReviewed,
            @RequestParam(defaultValue = "false") boolean waitingForReviewed,
            @RequestParam(defaultValue = "false") boolean somedayReviewed,
            @RequestParam(defaultValue = "false") boolean commitmentsReviewed,
            Authentication auth, RedirectAttributes redirect) {
        String target = "/productivity/weekly-review?weekStart=" + weekStart;
        return action(redirect, () -> productivity.saveWeeklyReview(owner(auth), weekStart, inboxProcessed,
                nextActionsReviewed, waitingForReviewed, somedayReviewed, commitmentsReviewed), target, "Weekly review updated.");
    }

    private String page(Model model, String title, String description) {
        model.addAttribute("pageTitle", title + " — TASK OS");
        model.addAttribute("description", description);
        model.addAttribute("activeNav", "productivity");
        return "productivity/index";
    }

    private String action(RedirectAttributes redirect, Runnable operation, String destination, String success) {
        try { operation.run(); redirect.addFlashAttribute("taskMessage", success); }
        catch (TaskRuleException ex) { redirect.addFlashAttribute("taskError", ex.getMessage()); }
        return "redirect:" + destination;
    }

    private UUID owner(Authentication authentication) { return ((TaskOsUserDetails) authentication.getPrincipal()).getId(); }
    private GtdState parseGtd(String value) { try { return "INBOX".equals(value) ? null : GtdState.valueOf(value); } catch (RuntimeException ex) { return null; } }
}
