package com.taskos.goals.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.goals.dto.ReviewForm;
import com.taskos.goals.entity.ReviewType;
import com.taskos.goals.service.ReviewService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.time.DayOfWeek;
import java.util.Locale;
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
@RequestMapping("/reviews")
public class ReviewController {
    private final ReviewService service;
    public ReviewController(ReviewService service) { this.service = service; }

    @GetMapping("/weekly")
    public String weekly(Authentication auth, @RequestParam(required = false) LocalDate period, Model model) {
        return page(auth, ReviewType.WEEKLY, period, model);
    }

    @GetMapping("/monthly")
    public String monthly(Authentication auth, @RequestParam(required = false) LocalDate period, Model model) {
        return page(auth, ReviewType.MONTHLY, period, model);
    }

    @PostMapping("/weekly")
    public String saveWeekly(Authentication auth, @RequestParam LocalDate period, @RequestParam(defaultValue = "false") boolean complete,
            @Valid ReviewForm form, BindingResult errors, RedirectAttributes flash) {
        return save(auth, ReviewType.WEEKLY, period, complete, form, errors, flash);
    }

    @PostMapping("/monthly")
    public String saveMonthly(Authentication auth, @RequestParam LocalDate period, @RequestParam(defaultValue = "false") boolean complete,
            @Valid ReviewForm form, BindingResult errors, RedirectAttributes flash) {
        return save(auth, ReviewType.MONTHLY, period, complete, form, errors, flash);
    }

    @GetMapping("/history")
    public String history(Authentication auth, Model model) {
        model.addAttribute("reviews", service.history(owner(auth))); model.addAttribute("activeNav", "reviews"); return "reviews/history";
    }

    @GetMapping("/{id}")
    public String detail(@PathVariable UUID id, Authentication auth, Model model) {
        var workspace = service.detail(owner(auth), id); model.addAttribute("reviewWorkspace", workspace);
        model.addAttribute("activeNav", "reviews"); model.addAttribute("reviewAction", workspace.review().getType() == ReviewType.WEEKLY ? "/reviews/weekly" : "/reviews/monthly");
        return "reviews/workspace";
    }

    private String page(Authentication auth, ReviewType type, LocalDate requested, Model model) {
        LocalDate start = normalize(type, requested == null ? service.currentStart(type, owner(auth)) : requested);
        var workspace = service.open(owner(auth), type, start); model.addAttribute("reviewWorkspace", workspace);
        model.addAttribute("activeNav", "reviews"); model.addAttribute("reviewAction", type == ReviewType.WEEKLY ? "/reviews/weekly" : "/reviews/monthly");
        return "reviews/workspace";
    }

    private String save(Authentication auth, ReviewType type, LocalDate period, boolean complete, ReviewForm form,
            BindingResult errors, RedirectAttributes flash) {
        LocalDate start = normalize(type, period);
        try { if (errors.hasErrors()) throw new IllegalArgumentException("Review text is longer than the allowed limit."); service.save(owner(auth), type, start, form, complete); flash.addFlashAttribute("taskMessage", complete ? "Review completed and saved." : "Review draft saved."); }
        catch (IllegalArgumentException e) { flash.addFlashAttribute("taskError", e.getMessage()); }
        return "redirect:/reviews/" + type.name().toLowerCase(Locale.ROOT) + "?period=" + start;
    }

    private LocalDate normalize(ReviewType type, LocalDate date) {
        if (date == null) throw new IllegalArgumentException("Choose a review period.");
        return type == ReviewType.WEEKLY ? date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : date.withDayOfMonth(1);
    }
    private UUID owner(Authentication auth) { return ((TaskOsUserDetails) auth.getPrincipal()).getId(); }
}
