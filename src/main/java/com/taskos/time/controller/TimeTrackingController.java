package com.taskos.time.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.task.entity.Task;
import com.taskos.task.repository.TaskRepository;
import com.taskos.time.dto.ManualTimeEntryForm;
import com.taskos.time.dto.TimeEntryView;
import com.taskos.time.dto.TrackingStartForm;
import com.taskos.time.entity.TimeEntry;
import com.taskos.time.entity.TimeEntryStatus;
import com.taskos.time.service.CalendarQueryService;
import com.taskos.time.service.ManualEntryMutation;
import com.taskos.time.service.TimeTrackingService;
import com.taskos.time.service.TimeZoneResolver;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
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
public class TimeTrackingController {
    private final TimeTrackingService tracking;
    private final CalendarQueryService calendar;
    private final TaskRepository tasks;
    private final TimeZoneResolver zones;
    private final Clock clock;
    public TimeTrackingController(TimeTrackingService tracking, CalendarQueryService calendar, TaskRepository tasks,
            TimeZoneResolver zones, Clock clock) {
        this.tracking = tracking; this.calendar = calendar; this.tasks = tasks; this.zones = zones; this.clock = clock;
    }

    @GetMapping("/time/tracking")
    public String tracking(@RequestParam(required = false) UUID edit, @RequestParam(required = false) UUID taskId,
            Authentication authentication, Model model) {
        UUID owner = owner(authentication); List<TimeEntry> history = tracking.history(owner);
        Map<UUID, String> taskTitles = tasks.findAllByOwner_IdAndIdIn(owner, history.stream().map(TimeEntry::getTaskId)
                .filter(id -> id != null).collect(Collectors.toSet())).stream().collect(Collectors.toMap(Task::getId, Task::getTitle));
        List<TimeEntryView> rows = history.stream().map(entry -> new TimeEntryView(entry,
                taskTitles.getOrDefault(entry.getTaskId(), entry.getTaskId() == null ? "Personal work" : "Deleted task"),
                entry.getStatus() == TimeEntryStatus.RUNNING ? tracking.elapsed(owner, entry.getId()) : entry.getAccumulatedSeconds())).toList();
        ManualTimeEntryForm manual = new ManualTimeEntryForm(); LocalDateTime now = LocalDateTime.now(clock.withZone(zones.zone(owner)));
        manual.setStartAt(now.minusMinutes(30)); manual.setEndAt(now);
        if (edit != null) {
            TimeEntry entry = history.stream().filter(item -> item.getId().equals(edit)).findFirst()
                    .orElseThrow(() -> new com.taskos.time.service.TimeRuleException("That manual time entry is not available."));
            if (entry.getSource() != com.taskos.time.entity.TimeEntrySource.MANUAL) throw new com.taskos.time.service.TimeRuleException("Only manual entries can be edited.");
            manual.setTaskId(entry.getTaskId()); manual.setDescription(entry.getDescription());
            manual.setStartAt(entry.getStartedAt().atZone(zones.zone(owner)).toLocalDateTime());
            manual.setEndAt(entry.getEndedAt().atZone(zones.zone(owner)).toLocalDateTime()); manual.setVersion(entry.getVersion());
            model.addAttribute("editingEntry", entry);
        }
        List<Task> taskOptions = new ArrayList<>(calendar.taskOptions(owner));
        TrackingStartForm startForm = new TrackingStartForm();
        if (taskId != null) {
            Task task = tasks.findDetailByIdAndOwner(taskId, owner)
                    .orElseThrow(() -> new com.taskos.time.service.TimeRuleException("That task is not available."));
            startForm.setTaskId(taskId);
            if (taskOptions.stream().noneMatch(option -> option.getId().equals(taskId))) taskOptions.add(task);
        }
        TimeEntry active = tracking.active(owner);
        model.addAttribute("timeEntries", rows); model.addAttribute("activeTracking", active);
        model.addAttribute("activeTrackingSeconds", active == null ? 0 : tracking.elapsed(owner, active.getId()));
        model.addAttribute("taskOptions", taskOptions); model.addAttribute("trackingStartForm", startForm);
        model.addAttribute("manualTimeEntryForm", manual); model.addAttribute("activeNav", "tracking");
        return "time/tracking";
    }

    @PostMapping("/time/tracking/start")
    public String start(@Valid @ModelAttribute("trackingStartForm") TrackingStartForm form, BindingResult binding,
            Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) { redirect.addFlashAttribute("timeError", "Check the task and description, then try again."); return "redirect:/time/tracking"; }
        tracking.start(owner(authentication), form); redirect.addFlashAttribute("timeMessage", "Work timer started."); return "redirect:/time/tracking";
    }
    @PostMapping("/time/tracking/{id}/pause")
    public String pause(@PathVariable UUID id, Authentication authentication) { tracking.pause(owner(authentication), id); return "redirect:/time/tracking"; }
    @PostMapping("/time/tracking/{id}/resume")
    public String resume(@PathVariable UUID id, Authentication authentication) { tracking.resume(owner(authentication), id); return "redirect:/time/tracking"; }
    @PostMapping("/time/tracking/{id}/stop")
    public String stop(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        tracking.stop(owner(authentication), id); redirect.addFlashAttribute("timeMessage", "Tracked time saved."); return "redirect:/time/tracking";
    }
    @PostMapping("/time/tracking/manual")
    public String manual(@Valid @ModelAttribute("manualTimeEntryForm") ManualTimeEntryForm form, BindingResult binding,
            Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) { redirect.addFlashAttribute("timeError", "Check the task and the start/end times."); return "redirect:/time/tracking"; }
        ManualEntryMutation saved = tracking.addManual(owner(authentication), form);
        redirect.addFlashAttribute("timeMessage", "Manual time entry saved.");
        if (saved.overlapWarning()) redirect.addFlashAttribute("timeWarning", "This entry overlaps other tracked work; actual totals merge overlapping intervals.");
        return "redirect:/time/tracking";
    }
    @PostMapping("/time/tracking/{id}/manual")
    public String editManual(@PathVariable UUID id, @Valid @ModelAttribute("manualTimeEntryForm") ManualTimeEntryForm form,
            BindingResult binding, Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) { redirect.addFlashAttribute("timeError", "Check the time entry and try again."); return "redirect:/time/tracking?edit=" + id; }
        boolean overlap = tracking.editManual(owner(authentication), id, form);
        redirect.addFlashAttribute("timeMessage", "Manual time entry updated.");
        if (overlap) redirect.addFlashAttribute("timeWarning", "This entry overlaps other tracked work; actual totals merge overlapping intervals.");
        return "redirect:/time/tracking";
    }
    @PostMapping("/time/tracking/{id}/delete")
    public String deleteManual(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        tracking.deleteManual(owner(authentication), id); redirect.addFlashAttribute("timeMessage", "Manual entry deleted."); return "redirect:/time/tracking";
    }

    private static UUID owner(Authentication authentication) { return ((TaskOsUserDetails) authentication.getPrincipal()).getId(); }
}
