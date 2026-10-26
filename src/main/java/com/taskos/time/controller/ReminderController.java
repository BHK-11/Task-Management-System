package com.taskos.time.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.time.dto.ReminderForm;
import com.taskos.time.service.CalendarQueryService;
import com.taskos.time.service.ReminderService;
import com.taskos.time.service.TimeBlockService;
import com.taskos.time.service.TimeZoneResolver;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
public class ReminderController {
    private final ReminderService reminders;
    private final CalendarQueryService calendar;
    private final TimeBlockService blocks;
    private final TimeZoneResolver zones;
    private final Clock clock;
    public ReminderController(ReminderService reminders, CalendarQueryService calendar, TimeBlockService blocks,
            TimeZoneResolver zones, Clock clock) {
        this.reminders = reminders; this.calendar = calendar; this.blocks = blocks; this.zones = zones; this.clock = clock;
    }
    @GetMapping("/reminders")
    public String page(Authentication authentication, Model model) {
        UUID owner = owner(authentication); ZoneId zone = zones.zone(owner); ReminderForm form = new ReminderForm();
        form.setScheduledFor(LocalDateTime.now(clock.withZone(zone)).plusHours(1));
        model.addAttribute("reminderForm", form); model.addAttribute("reminders", reminders.list(owner));
        model.addAttribute("notifications", reminders.inbox(owner)); model.addAttribute("taskOptions", calendar.taskOptions(owner));
        model.addAttribute("timeBlocks", blocks.all(owner)); model.addAttribute("reminderZone", zone.getId());
        model.addAttribute("activeNav", "reminders"); return "time/reminders";
    }
    @PostMapping("/reminders")
    public String create(@Valid @ModelAttribute("reminderForm") ReminderForm form, BindingResult binding,
            Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) { redirect.addFlashAttribute("timeError", "Check the reminder title, target and time."); return "redirect:/reminders"; }
        reminders.create(owner(authentication), form); redirect.addFlashAttribute("timeMessage", "In-app reminder scheduled."); return "redirect:/reminders";
    }
    @PostMapping("/reminders/{id}/snooze")
    public String snooze(@PathVariable UUID id, @RequestParam LocalDateTime scheduledFor,
            Authentication authentication, RedirectAttributes redirect) {
        reminders.snooze(owner(authentication), id, scheduledFor); redirect.addFlashAttribute("timeMessage", "Reminder snoozed."); return "redirect:/reminders";
    }
    @PostMapping("/reminders/{id}/dismiss")
    public String dismiss(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        reminders.dismiss(owner(authentication), id); redirect.addFlashAttribute("timeMessage", "Reminder dismissed."); return "redirect:/reminders";
    }
    @PostMapping("/reminders/notifications/{id}/read")
    public String read(@PathVariable UUID id, Authentication authentication) { reminders.read(owner(authentication), id); return "redirect:/reminders"; }
    private static UUID owner(Authentication authentication) { return ((TaskOsUserDetails) authentication.getPrincipal()).getId(); }
}
