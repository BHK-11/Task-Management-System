package com.taskos.time.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.task.entity.Task;
import com.taskos.task.repository.TaskRepository;
import com.taskos.time.dto.CalendarPage;
import com.taskos.time.dto.TimeBlockForm;
import com.taskos.time.service.CalendarQueryService;
import com.taskos.time.service.FocusSessionService;
import com.taskos.time.service.TimeBlockService;
import com.taskos.time.service.TimeInsightsService;
import com.taskos.time.service.TimeTrackingService;
import com.taskos.time.service.TimeZoneResolver;
import com.taskos.time.service.BlockMutation;
import com.taskos.time.service.ReminderService;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
public class TimeController {
    private final CalendarQueryService calendar;
    private final TimeBlockService blocks;
    private final FocusSessionService focus;
    private final TimeTrackingService tracking;
    private final TimeInsightsService insights;
    private final ReminderService reminders;
    private final TimeZoneResolver zones;
    private final Clock clock;
    private final TaskRepository tasks;

    public TimeController(CalendarQueryService calendar, TimeBlockService blocks, FocusSessionService focus,
            TimeTrackingService tracking, TimeInsightsService insights, ReminderService reminders,
            TimeZoneResolver zones, Clock clock, TaskRepository tasks) {
        this.calendar = calendar; this.blocks = blocks; this.focus = focus; this.tracking = tracking;
        this.insights = insights; this.reminders = reminders; this.zones = zones; this.clock = clock; this.tasks = tasks;
    }

    @GetMapping("/time")
    public String hub(Authentication authentication, Model model) {
        UUID owner = owner(authentication); LocalDate today = LocalDate.now(clock.withZone(zones.zone(owner)));
        CalendarPage todayPage = calendar.page(owner, "day", today);
        var next = todayPage.events().stream().filter(event -> event.type().equals("TIME_BLOCK")
                && event.start() != null && event.start().isAfter(LocalDateTime.now(clock.withZone(zones.zone(owner)))))
                .findFirst().orElse(null);
        model.addAttribute("timeHub", true); model.addAttribute("todayPage", todayPage);
        model.addAttribute("nextBlock", next); model.addAttribute("activeFocus", focus.active(owner));
        model.addAttribute("activeTracking", tracking.active(owner)); model.addAttribute("todayTrackedSeconds", insights.page(owner).todaySeconds());
        model.addAttribute("pendingReminders", reminders.pendingCount(owner)); model.addAttribute("activeNav", "time");
        return "time/hub";
    }

    @GetMapping("/calendar")
    public String calendar(@RequestParam(defaultValue = "month") String view,
            @RequestParam(required = false) LocalDate date, @RequestParam(required = false) UUID taskId,
            Authentication authentication, Model model) {
        UUID owner = owner(authentication); CalendarPage page = calendar.page(owner, view, date);
        List<Task> taskOptions = new ArrayList<>(calendar.taskOptions(owner));
        TimeBlockForm form = newBlockForm(page.selectedDate());
        if (taskId != null) {
            Task task = tasks.findDetailByIdAndOwner(taskId, owner)
                    .orElseThrow(() -> new com.taskos.time.service.TimeRuleException("That task is not available."));
            form.setTaskId(taskId);
            if (taskOptions.stream().noneMatch(option -> option.getId().equals(taskId))) taskOptions.add(task);
        }
        model.addAttribute("calendar", page); model.addAttribute("calendarView", page.view());
        model.addAttribute("hourRows", java.util.stream.IntStream.range(0, 24).boxed().toList());
        model.addAttribute("taskOptions", taskOptions); model.addAttribute("activeNav", "calendar");
        model.addAttribute("timeBlockForm", form);
        return "time/calendar";
    }

    @PostMapping("/time/blocks")
    public String createBlock(@Valid @ModelAttribute("timeBlockForm") TimeBlockForm form, BindingResult binding,
            Authentication authentication, RedirectAttributes redirect,
            @RequestParam(defaultValue = "calendar") String returnTo,
            @RequestParam(defaultValue = "day") String view, @RequestParam(required = false) LocalDate date) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("timeError", "Check the title, task and start/end times, then try again.");
            return redirectCalendar(view, date);
        }
        BlockMutation saved = blocks.create(owner(authentication), form);
        redirect.addFlashAttribute("timeMessage", "Time block saved.");
        if (saved.conflictWarning()) redirect.addFlashAttribute("timeWarning", "This block overlaps another planned block. Both remain scheduled.");
        if ("blocks".equals(returnTo)) return "redirect:/time/blocks";
        return redirectCalendar(view, form.getStartAt().toLocalDate());
    }

    @GetMapping("/time/blocks")
    public String blockList(@RequestParam(required = false) UUID edit, Authentication authentication, Model model) {
        UUID owner = owner(authentication); List<Task> tasks = calendar.taskOptions(owner);
        TimeBlockForm form;
        if (edit == null) form = newBlockForm(LocalDate.now(clock.withZone(zones.zone(owner))));
        else {
            var block = blocks.get(owner, edit); form = new TimeBlockForm(); form.setTitle(block.getTitle());
            form.setTaskId(block.getTaskId()); form.setStartAt(block.getStartAt().atZone(zones.zone(owner)).toLocalDateTime());
            form.setEndAt(block.getEndAt().atZone(zones.zone(owner)).toLocalDateTime()); form.setNotes(block.getNotes());
            form.setVersion(block.getVersion()); model.addAttribute("editingBlock", block);
        }
        model.addAttribute("timeBlocks", blocks.all(owner)); model.addAttribute("taskOptions", tasks);
        model.addAttribute("timeBlockForm", form); model.addAttribute("activeNav", "time-blocks");
        return "time/blocks";
    }

    @PostMapping("/time/blocks/{id}")
    public String updateBlock(@PathVariable UUID id, @Valid @ModelAttribute("timeBlockForm") TimeBlockForm form,
            BindingResult binding, Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) { redirect.addFlashAttribute("timeError", "Check the block details and try again."); return "redirect:/time/blocks?edit=" + id; }
        boolean conflict = blocks.update(owner(authentication), id, form);
        redirect.addFlashAttribute("timeMessage", "Time block updated.");
        if (conflict) redirect.addFlashAttribute("timeWarning", "This block overlaps another planned block. Both remain scheduled.");
        return "redirect:/time/blocks";
    }

    @PostMapping("/time/blocks/{id}/move")
    public String moveBlock(@PathVariable UUID id, @RequestParam LocalDateTime startAt,
            @RequestParam LocalDateTime endAt, @RequestParam long version,
            Authentication authentication, RedirectAttributes redirect) {
        UUID owner = owner(authentication); var block = blocks.get(owner, id); TimeBlockForm form = new TimeBlockForm();
        form.setTitle(block.getTitle()); form.setTaskId(block.getTaskId()); form.setNotes(block.getNotes());
        form.setStartAt(startAt); form.setEndAt(endAt); form.setVersion(version);
        boolean conflict = blocks.update(owner, id, form);
        if (conflict) redirect.addFlashAttribute("timeWarning", "Moved block overlaps another planned block. Both remain scheduled.");
        redirect.addFlashAttribute("timeMessage", "Time block moved.");
        return redirectCalendar("week", startAt.toLocalDate());
    }

    @PostMapping("/time/blocks/{id}/delete")
    public String deleteBlock(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        blocks.delete(owner(authentication), id); redirect.addFlashAttribute("timeMessage", "Time block deleted.");
        return "redirect:/time/blocks";
    }

    private static TimeBlockForm newBlockForm(LocalDate date) {
        TimeBlockForm form = new TimeBlockForm(); LocalDateTime start = date.atTime(9, 0);
        form.setStartAt(start); form.setEndAt(start.plusHours(1)); return form;
    }
    private static String redirectCalendar(String view, LocalDate date) {
        String safeView = List.of("month", "week", "day").contains(view) ? view : "day";
        return "redirect:/calendar?view=" + safeView + (date == null ? "" : "&date=" + date);
    }
    private static UUID owner(Authentication authentication) { return ((TaskOsUserDetails) authentication.getPrincipal()).getId(); }
}
