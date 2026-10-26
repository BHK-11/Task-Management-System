package com.taskos.time.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.task.entity.Task;
import com.taskos.task.repository.TaskRepository;
import com.taskos.time.dto.RecurrenceForm;
import com.taskos.time.entity.MonthEndPolicy;
import com.taskos.time.entity.RecurrenceFrequency;
import com.taskos.time.service.CalendarQueryService;
import com.taskos.time.service.RecurrenceService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class RecurrenceController {
    private final RecurrenceService recurrences;
    private final TaskRepository tasks;
    private final CalendarQueryService calendar;
    private final com.taskos.time.service.TimeZoneResolver zones;
    private final Clock clock;
    public RecurrenceController(RecurrenceService recurrences, TaskRepository tasks, CalendarQueryService calendar,
            com.taskos.time.service.TimeZoneResolver zones, Clock clock) {
        this.recurrences = recurrences; this.tasks = tasks; this.calendar = calendar; this.zones = zones; this.clock = clock;
    }
    @GetMapping("/tasks/recurring")
    public String page(Authentication authentication, Model model) {
        UUID owner = owner(authentication); ZoneId zone = zones.zone(owner);
        RecurrenceForm form = new RecurrenceForm(); form.setStartDate(LocalDate.now(clock.withZone(zone)));
        form.setLocalTime(LocalTime.of(9, 0));
        var rules = recurrences.list(owner);
        var templateIds = rules.stream().map(rule -> rule.getSourceTaskId()).collect(Collectors.toSet());
        Map<UUID, String> templateTitles = templateIds.isEmpty() ? Map.of()
                : tasks.findAllByOwner_IdAndIdIn(owner, templateIds).stream()
                        .collect(Collectors.toMap(Task::getId, Task::getTitle));
        model.addAttribute("recurrenceForm", form); model.addAttribute("recurrenceRules", rules);
        model.addAttribute("templateTitles", templateTitles);
        model.addAttribute("recurrenceOccurrences", recurrences.recentOccurrences(owner));
        model.addAttribute("taskOptions", calendar.taskOptions(owner)); model.addAttribute("frequencies", RecurrenceFrequency.values());
        model.addAttribute("monthEndPolicies", MonthEndPolicy.values()); model.addAttribute("recurrenceZone", zone.getId());
        model.addAttribute("activeNav", "recurring"); return "time/recurring";
    }
    @PostMapping("/tasks/recurring")
    public String create(@Valid @ModelAttribute("recurrenceForm") RecurrenceForm form, BindingResult binding,
            Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) { redirect.addFlashAttribute("timeError", "Check the task, recurrence pattern and dates."); return "redirect:/tasks/recurring"; }
        recurrences.create(owner(authentication), form); redirect.addFlashAttribute("timeMessage", "Recurring task rule saved.");
        return "redirect:/tasks/recurring";
    }
    @PostMapping("/tasks/recurring/{id}/pause")
    public String pause(@PathVariable UUID id, Authentication authentication) { recurrences.pause(owner(authentication), id, true); return "redirect:/tasks/recurring"; }
    @PostMapping("/tasks/recurring/{id}/resume")
    public String resume(@PathVariable UUID id, Authentication authentication) { recurrences.pause(owner(authentication), id, false); return "redirect:/tasks/recurring"; }
    @PostMapping("/tasks/recurring/{id}/end")
    public String end(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        recurrences.end(owner(authentication), id); redirect.addFlashAttribute("timeMessage", "Recurrence series ended."); return "redirect:/tasks/recurring";
    }
    @PostMapping("/tasks/recurring/{id}/skip")
    public String skip(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        recurrences.skipNext(owner(authentication), id); redirect.addFlashAttribute("timeMessage", "The next occurrence was skipped."); return "redirect:/tasks/recurring";
    }
    @PostMapping("/tasks/recurring/{id}/reschedule")
    public String reschedule(@PathVariable UUID id, @RequestParam LocalDate date,
            Authentication authentication, RedirectAttributes redirect) {
        UUID taskId = recurrences.rescheduleNext(owner(authentication), id, date);
        redirect.addFlashAttribute("timeMessage", "Next occurrence rescheduled as a task."); return "redirect:/tasks/" + taskId;
    }
    private static UUID owner(Authentication authentication) { return ((TaskOsUserDetails) authentication.getPrincipal()).getId(); }
}
