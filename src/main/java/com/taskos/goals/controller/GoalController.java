package com.taskos.goals.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.goals.dto.GoalForm;
import com.taskos.goals.dto.GoalListItem;
import com.taskos.goals.dto.GoalMeasurementForm;
import com.taskos.goals.entity.GoalCategory;
import com.taskos.goals.entity.GoalMetric;
import com.taskos.goals.entity.GoalStatus;
import com.taskos.goals.service.GoalService;
import com.taskos.time.service.ReminderService;
import com.taskos.time.service.TimeZoneResolver;
import com.taskos.time.service.TimeRuleException;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/goals")
public class GoalController {
    private final GoalService service;
    private final Clock clock;
    private final ReminderService reminders;
    private final TimeZoneResolver zones;
    public GoalController(GoalService service, Clock clock, ReminderService reminders, TimeZoneResolver zones) { this.service = service; this.clock = clock; this.reminders = reminders; this.zones = zones; }

    @GetMapping
    public String index(Authentication auth, Model model) {
        UUID owner = owner(auth);
        model.addAttribute("goalItems", service.listItems(owner));
        model.addAttribute("activeGoalCount", service.activeCount(owner)); model.addAttribute("activeNav", "goals");
        return "goals/index";
    }

    @GetMapping("/new")
    public String createForm(Authentication auth, Model model) {
        GoalForm form = new GoalForm(); form.setStartDate(today(auth));
        model.addAttribute("goalForm", form); model.addAttribute("smartGuidance", service.qualityGuidance(form)); addFormOptions(auth, model); model.addAttribute("activeNav", "goals"); model.addAttribute("editingGoal", false);
        return "goals/form";
    }

    @PostMapping
    public String create(Authentication auth, @Valid GoalForm form, BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) { model.addAttribute("smartGuidance", service.qualityGuidance(form)); addFormOptions(auth, model); model.addAttribute("activeNav", "goals"); model.addAttribute("editingGoal", false); return "goals/form"; }
        try { return "redirect:/goals/" + service.create(owner(auth), form); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); return "redirect:/goals/new"; }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Authentication auth, Model model) {
        var detail = service.detail(owner(auth), id); model.addAttribute("goalDetail", detail);
        GoalMeasurementForm measurement = new GoalMeasurementForm(); measurement.setDate(today(auth));
        model.addAttribute("measurementForm", measurement); model.addAttribute("taskOptions", service.taskOptions(owner(auth)));
        model.addAttribute("reminderTime", LocalDateTime.now(clock.withZone(zones.zone(owner(auth)))).plusHours(1));
        model.addAttribute("reminderZone", zones.zone(owner(auth)).getId());
        model.addAttribute("activeNav", "goals"); return "goals/detail";
    }

    @GetMapping("/{id}/edit")
    public String editForm(@PathVariable UUID id, Authentication auth, Model model) {
        GoalForm form = service.form(owner(auth), id);
        model.addAttribute("goalForm", form); model.addAttribute("goalId", id);
        model.addAttribute("smartGuidance", service.qualityGuidance(form));
        model.addAttribute("editingGoal", true); addFormOptions(auth, model); model.addAttribute("activeNav", "goals"); return "goals/form";
    }

    @PostMapping("/{id}")
    public String update(@PathVariable UUID id, Authentication auth, @Valid GoalForm form, BindingResult errors, Model model, RedirectAttributes flash) {
        if (errors.hasErrors()) { model.addAttribute("goalId", id); model.addAttribute("smartGuidance", service.qualityGuidance(form)); model.addAttribute("editingGoal", true); addFormOptions(auth, model); model.addAttribute("activeNav", "goals"); return "goals/form"; }
        try { service.update(owner(auth), id, form); flash.addFlashAttribute("taskMessage", "Goal details saved."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals/" + id;
    }

    @PostMapping("/{id}/check-ins")
    public String checkIn(@PathVariable UUID id, Authentication auth, @Valid GoalMeasurementForm form, BindingResult errors, RedirectAttributes flash) {
        try { if (errors.hasErrors()) throw new IllegalArgumentException("Enter a valid value and measurement date."); service.record(owner(auth), id, form.getValue(), form.getDate(), form.getNotes()); flash.addFlashAttribute("taskMessage", "Measurement saved to goal history."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals/" + id;
    }

    @PostMapping("/{id}/reminders")
    public String reminder(@PathVariable UUID id, @RequestParam LocalDateTime scheduledFor, Authentication auth, RedirectAttributes flash) {
        try { reminders.createGoalCheckIn(owner(auth), id, scheduledFor); flash.addFlashAttribute("taskMessage", "In-app goal check-in reminder scheduled."); }
        catch (IllegalArgumentException | TimeRuleException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals/" + id;
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable UUID id, @RequestParam GoalStatus status, Authentication auth, RedirectAttributes flash) {
        try { service.transition(owner(auth), id, status); flash.addFlashAttribute("taskMessage", "Goal status updated."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals/" + id;
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable UUID id, Authentication auth, RedirectAttributes flash) {
        try { service.archive(owner(auth), id); flash.addFlashAttribute("taskMessage", "Goal archived. Its measurements, milestones, and linked work are preserved."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals";
    }

    @PostMapping("/{id}/tasks")
    public String linkTask(@PathVariable UUID id, @RequestParam UUID taskId, Authentication auth, RedirectAttributes flash) {
        try { service.linkTask(owner(auth), id, taskId); flash.addFlashAttribute("taskMessage", "Canonical task linked to this goal."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals/" + id;
    }

    @PostMapping("/{id}/tasks/{taskId}/remove")
    public String unlinkTask(@PathVariable UUID id, @PathVariable UUID taskId, Authentication auth, RedirectAttributes flash) {
        try { service.unlinkTask(owner(auth), id, taskId); flash.addFlashAttribute("taskMessage", "Task unlinked. The task itself was kept."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals/" + id;
    }

    @PostMapping("/{id}/milestones")
    public String addMilestone(@PathVariable UUID id, @RequestParam String title, @RequestParam(defaultValue = "") String description,
            @RequestParam(required = false) LocalDate targetDate, Authentication auth, RedirectAttributes flash) {
        try { service.addMilestone(owner(auth), id, title, description, targetDate); flash.addFlashAttribute("taskMessage", "Milestone added to the roadmap."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals/" + id + "/roadmap";
    }

    @PostMapping("/{id}/milestones/{milestoneId}/toggle")
    public String toggleMilestone(@PathVariable UUID id, @PathVariable UUID milestoneId, Authentication auth, RedirectAttributes flash) {
        try { service.toggleMilestone(owner(auth), id, milestoneId); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals/" + id + "/roadmap";
    }

    @PostMapping("/{id}/milestones/{milestoneId}")
    public String updateMilestone(@PathVariable UUID id, @PathVariable UUID milestoneId, @RequestParam String title,
            @RequestParam(defaultValue = "") String description, @RequestParam(required = false) LocalDate targetDate,
            Authentication auth, RedirectAttributes flash) {
        try { service.updateMilestone(owner(auth), id, milestoneId, title, description, targetDate); flash.addFlashAttribute("taskMessage", "Milestone updated."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals/" + id + "/roadmap";
    }

    @PostMapping("/{id}/milestones/{milestoneId}/move")
    public String moveMilestone(@PathVariable UUID id, @PathVariable UUID milestoneId, @RequestParam int direction,
            Authentication auth, RedirectAttributes flash) {
        try { service.moveMilestone(owner(auth), id, milestoneId, direction); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals/" + id + "/roadmap";
    }

    @PostMapping("/{id}/milestones/{milestoneId}/delete")
    public String deleteMilestone(@PathVariable UUID id, @PathVariable UUID milestoneId, Authentication auth, RedirectAttributes flash) {
        try { service.deleteMilestone(owner(auth), id, milestoneId); flash.addFlashAttribute("taskMessage", "Milestone removed from the roadmap."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/goals/" + id + "/roadmap";
    }

    @GetMapping("/{id}/roadmap")
    public String roadmap(@PathVariable UUID id, Authentication auth, Model model) {
        model.addAttribute("goalDetail", service.detail(owner(auth), id)); model.addAttribute("activeNav", "goals"); return "goals/roadmap";
    }

    @GetMapping("/insights")
    public String insights(Authentication auth, Model model) {
        var goals = service.list(owner(auth)); LocalDate today = today(auth);
        model.addAttribute("goals", goals); model.addAttribute("activeGoals", goals.stream().filter(g -> g.getStatus() == GoalStatus.ACTIVE).count());
        model.addAttribute("overdueGoals", goals.stream().filter(g -> g.getStatus() == GoalStatus.ACTIVE && g.getTargetDate() != null && g.getTargetDate().isBefore(today)).count());
        model.addAttribute("completedGoals", goals.stream().filter(g -> g.getStatus() == GoalStatus.COMPLETED).count());
        model.addAttribute("activeNav", "goals"); return "goals/insights";
    }

    private void addFormOptions(Authentication auth, Model model) {
        UUID owner = owner(auth); model.addAttribute("categories", GoalCategory.values()); model.addAttribute("metrics", GoalMetric.values());
        model.addAttribute("projects", service.projectOptions(owner));
    }
    private LocalDate today(Authentication auth) { return LocalDate.now(clock.withZone(zones.zone(owner(auth)))); }
    private UUID owner(Authentication auth) { return ((TaskOsUserDetails) auth.getPrincipal()).getId(); }
}
