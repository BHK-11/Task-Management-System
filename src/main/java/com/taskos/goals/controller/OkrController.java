package com.taskos.goals.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.goals.dto.KeyResultForm;
import com.taskos.goals.dto.KeyResultMeasurementForm;
import com.taskos.goals.dto.OkrForm;
import com.taskos.goals.service.GoalService;
import com.taskos.goals.service.OkrService;
import com.taskos.time.service.TimeZoneResolver;
import jakarta.validation.Valid;
import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
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
@RequestMapping("/okrs")
public class OkrController {
    private final OkrService service;
    private final GoalService goals;
    private final Clock clock;
    private final TimeZoneResolver zones;
    public OkrController(OkrService service, GoalService goals, Clock clock, TimeZoneResolver zones) { this.service = service; this.goals = goals; this.clock = clock; this.zones = zones; }

    @GetMapping
    public String index(Authentication auth, @RequestParam(required = false) UUID cycleId, Model model) {
        UUID owner = owner(auth); var cycles = service.cycles(owner);
        if (cycleId == null && !cycles.isEmpty()) cycleId = cycles.getFirst().getId();
        model.addAttribute("cycles", cycles); model.addAttribute("selectedCycle", cycleId);
        model.addAttribute("objectives", cycleId == null ? java.util.List.of() : service.objectives(owner, cycleId));
        model.addAttribute("activeNav", "okrs"); return "okrs/index";
    }

    @GetMapping("/new")
    public String createForm(Authentication auth, Model model) {
        OkrForm form = new OkrForm(); LocalDate today = today(auth); YearMonth month = YearMonth.from(today);
        form.setCycleName(month.getMonth().name().substring(0, 1) + month.getMonth().name().substring(1).toLowerCase() + " " + month.getYear());
        form.setCycleStart(month.atDay(1)); form.setCycleEnd(month.atEndOfMonth()); model.addAttribute("okrForm", form);
        model.addAttribute("goals", goals.list(owner(auth))); model.addAttribute("activeNav", "okrs"); return "okrs/form";
    }

    @PostMapping
    public String create(Authentication auth, @Valid OkrForm form, BindingResult errors, RedirectAttributes flash) {
        try {
            if (errors.hasErrors()) throw new IllegalArgumentException("Complete the objective and review cycle fields.");
            UUID id = service.createObjective(owner(auth), form); flash.addFlashAttribute("taskMessage", "Objective created. Add its measurable key results next."); return "redirect:/okrs/" + id;
        } catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); return "redirect:/okrs/new"; }
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Authentication auth, Model model) {
        KeyResultMeasurementForm measurement = new KeyResultMeasurementForm(); measurement.setDate(today(auth));
        model.addAttribute("objectiveDetail", service.detail(owner(auth), id)); model.addAttribute("keyResultForm", new KeyResultForm());
        model.addAttribute("measurementForm", measurement); model.addAttribute("activeNav", "okrs"); return "okrs/detail";
    }

    @PostMapping("/{id}/key-results")
    public String addKeyResult(@PathVariable UUID id, Authentication auth, @Valid KeyResultForm form, BindingResult errors, RedirectAttributes flash) {
        try { if (errors.hasErrors()) throw new IllegalArgumentException("Complete the key result fields with a valid target range."); service.addKeyResult(owner(auth), id, form); flash.addFlashAttribute("taskMessage", "Key result added."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/okrs/" + id;
    }

    @PostMapping("/{id}/key-results/{resultId}/measurements")
    public String measure(@PathVariable UUID id, @PathVariable UUID resultId, Authentication auth,
            @Valid KeyResultMeasurementForm form, BindingResult errors, RedirectAttributes flash) {
        try { if (errors.hasErrors()) throw new IllegalArgumentException("Enter a valid value and date."); service.record(owner(auth), id, resultId, form); flash.addFlashAttribute("taskMessage", "Key result check-in saved."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/okrs/" + id;
    }

    @PostMapping("/{id}/status")
    public String status(@PathVariable UUID id, @RequestParam String status, Authentication auth, RedirectAttributes flash) {
        try { service.transition(owner(auth), id, status); flash.addFlashAttribute("taskMessage", "Objective status updated."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/okrs/" + id;
    }

    private LocalDate today(Authentication auth) { return LocalDate.now(clock.withZone(zones.zone(owner(auth)))); }
    private UUID owner(Authentication auth) { return ((TaskOsUserDetails) auth.getPrincipal()).getId(); }
}
