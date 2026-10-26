package com.taskos.habits.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.goals.service.GoalService;
import com.taskos.habits.dto.HabitCheckInForm;
import com.taskos.habits.dto.HabitForm;
import com.taskos.habits.dto.HabitListItem;
import com.taskos.habits.entity.HabitFrequency;
import com.taskos.habits.service.HabitService;
import com.taskos.time.service.TimeZoneResolver;
import com.taskos.time.service.ReminderService;
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
@RequestMapping("/habits")
public class HabitController {
    private final HabitService service;
    private final GoalService goals;
    private final TimeZoneResolver zones;
    private final Clock clock;
    private final ReminderService reminders;
    public HabitController(HabitService service, GoalService goals, TimeZoneResolver zones, Clock clock, ReminderService reminders) { this.service = service; this.goals = goals; this.zones = zones; this.clock = clock; this.reminders = reminders; }

    @GetMapping
    public String index(Authentication auth, Model model) {
        UUID owner = owner(auth);
        model.addAttribute("habitItems", service.list(owner).stream().map(habit -> new HabitListItem(habit, service.stats(owner, habit.getId()))).toList());
        model.addAttribute("activeNav", "habits"); return "habits/index";
    }

    @GetMapping("/new")
    public String createForm(Authentication auth, Model model) {
        HabitForm form = new HabitForm(); form.setStartDate(today(auth)); form.setEffectiveDate(today(auth).plusDays(1));
        form.setTimezone(zones.zone(owner(auth)).getId()); model.addAttribute("habitForm", form); addOptions(auth, model);
        model.addAttribute("frequencies", HabitFrequency.values()); model.addAttribute("activeNav", "habits"); return "habits/form";
    }

    @PostMapping
    public String create(Authentication auth, @Valid HabitForm form, BindingResult errors, RedirectAttributes flash) {
        try { if (errors.hasErrors()) throw new IllegalArgumentException("Complete the habit fields with valid schedule and date values."); UUID id = service.create(owner(auth), form); flash.addFlashAttribute("taskMessage", "Habit created."); return "redirect:/habits/" + id; }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); return "redirect:/habits/new"; }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Authentication auth, Model model) {
        var detail = service.detail(owner(auth), id); HabitForm edit = new HabitForm();
        edit.setName(detail.habit().getName()); edit.setDescription(detail.habit().getDescription());
        edit.setStartDate(detail.habit().getStartDate()); edit.setTimezone(detail.habit().getTimezoneId());
        if (!detail.schedules().isEmpty()) {
            var current = detail.schedules().getLast(); edit.setFrequency(current.getFrequency()); edit.setSelectedWeekdays(current.getSelectedWeekdays());
            edit.setTargetCount(current.getTargetCount()); edit.setTargetQuantity(current.getTargetQuantity()); edit.setUnit(current.getUnit());
        }
        edit.setEffectiveDate(nextEffective(today(auth), edit.getFrequency())); edit.setLinkedGoalId(detail.habit().getLinkedGoalId()); edit.setLinkedTaskId(detail.habit().getLinkedTaskId());
        HabitCheckInForm checkIn = new HabitCheckInForm(); checkIn.setDate(today(auth));
        model.addAttribute("habitDetail", detail); model.addAttribute("habitForm", edit); model.addAttribute("checkInForm", checkIn);
        model.addAttribute("reminderTime", LocalDateTime.now(clock.withZone(java.time.ZoneId.of(detail.habit().getTimezoneId()))).plusHours(1));
        model.addAttribute("reminderZone", detail.habit().getTimezoneId());
        model.addAttribute("frequencies", HabitFrequency.values()); addOptions(auth, model); model.addAttribute("activeNav", "habits"); return "habits/detail";
    }

    @PostMapping("/{id}/update")
    public String update(@PathVariable UUID id, Authentication auth, @Valid HabitForm form, BindingResult errors, RedirectAttributes flash) {
        try { if (errors.hasErrors()) throw new IllegalArgumentException("Complete the habit fields with valid values."); service.update(owner(auth), id, form); flash.addFlashAttribute("taskMessage", "Habit details and its future schedule were saved."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/habits/" + id;
    }

    @PostMapping("/{id}/check-ins")
    public String checkIn(@PathVariable UUID id, Authentication auth, @Valid HabitCheckInForm form, BindingResult errors, RedirectAttributes flash) {
        try { if (errors.hasErrors()) throw new IllegalArgumentException("Enter a valid date and quantity."); service.checkIn(owner(auth), id, form.getDate(), form.getQuantity(), form.isPerformed(), form.getNotes()); flash.addFlashAttribute("taskMessage", "Habit check-in saved to your history."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/habits/" + id;
    }

    @PostMapping("/{id}/reminders")
    public String reminder(@PathVariable UUID id, @RequestParam LocalDateTime scheduledFor, Authentication auth, RedirectAttributes flash) {
        try { reminders.createHabitCheckIn(owner(auth), id, scheduledFor); flash.addFlashAttribute("taskMessage", "In-app habit reminder scheduled."); }
        catch (IllegalArgumentException | TimeRuleException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/habits/" + id;
    }

    @PostMapping("/{id}/pause")
    public String pause(@PathVariable UUID id, Authentication auth, RedirectAttributes flash) {
        try { service.pause(owner(auth), id); flash.addFlashAttribute("taskMessage", "Habit paused. Paused periods are excluded from streak eligibility."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/habits/" + id;
    }

    @PostMapping("/{id}/resume")
    public String resume(@PathVariable UUID id, Authentication auth, RedirectAttributes flash) {
        try { service.resume(owner(auth), id); flash.addFlashAttribute("taskMessage", "Habit resumed."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/habits/" + id;
    }

    @PostMapping("/{id}/archive")
    public String archive(@PathVariable UUID id, Authentication auth, RedirectAttributes flash) {
        try { service.archive(owner(auth), id); flash.addFlashAttribute("taskMessage", "Habit archived. Its schedule and check-in history remain available."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/habits";
    }

    @GetMapping("/calendar")
    public String calendar(Authentication auth, @RequestParam(required = false) UUID habitId, Model model) {
        var habits = service.list(owner(auth)); model.addAttribute("habits", habits);
        if (habitId == null && !habits.isEmpty()) habitId = habits.getFirst().getId();
        model.addAttribute("habitDetail", habitId == null ? null : service.detail(owner(auth), habitId));
        model.addAttribute("activeNav", "habits"); return "habits/calendar";
    }

    private LocalDate today(Authentication auth) { return LocalDate.now(clock.withZone(zones.zone(owner(auth)))); }
    private LocalDate nextEffective(LocalDate today, HabitFrequency frequency) {
        return switch (frequency) {
            case WEEKLY_TARGET -> today.with(java.time.temporal.TemporalAdjusters.next(java.time.DayOfWeek.MONDAY));
            case MONTHLY_TARGET -> today.withDayOfMonth(1).plusMonths(1);
            default -> today.plusDays(1);
        };
    }
    private void addOptions(Authentication auth, Model model) {
        model.addAttribute("goals", goals.list(owner(auth))); model.addAttribute("taskOptions", goals.taskOptions(owner(auth)));
    }
    private UUID owner(Authentication auth) { return ((TaskOsUserDetails) auth.getPrincipal()).getId(); }
}
