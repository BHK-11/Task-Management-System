package com.taskos.browser.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.browser.dto.BrowserObserveForm;
import com.taskos.browser.entity.BrowserJobStatus;
import com.taskos.browser.service.BrowserObservationJobService;
import com.taskos.browser.service.BrowserObservationNotFoundException;
import com.taskos.browser.service.BrowserWorkerClient;
import com.taskos.browser.service.BrowserWorkerException;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/browser")
public class BrowserStudioController {
    private final BrowserObservationJobService jobs;
    private final BrowserWorkerClient browser;

    public BrowserStudioController(BrowserObservationJobService jobs, BrowserWorkerClient browser) {
        this.jobs = jobs;
        this.browser = browser;
    }

    @GetMapping
    public String index(Authentication authentication, Model model) {
        prepare(authentication, model);
        return "browser/studio";
    }

    @PostMapping("/observe")
    public String observe(@Valid @ModelAttribute("browserObserveForm") BrowserObserveForm form, BindingResult binding,
            Authentication authentication, Model model) {
        if (binding.hasErrors()) {
            prepare(authentication, model);
            model.addAttribute("browserError", "Enter a public HTTPS URL up to 2,048 characters.");
            return "browser/studio";
        }
        URI target;
        try {
            target = BrowserWorkerClient.target(form.getUrl());
        } catch (BrowserWorkerException ex) {
            prepare(authentication, model);
            model.addAttribute("browserError", ex.getMessage());
            return "browser/studio";
        }
        var ownerId = actor(authentication);
        try {
            jobs.ensureCapacity(ownerId);
        } catch (BrowserWorkerException ex) {
            prepare(authentication, model);
            model.addAttribute("browserError", ex.getMessage());
            return "browser/studio";
        }
        String origin = target.getScheme() + "://" + target.getAuthority();
        com.taskos.browser.dto.BrowserObservation observation;
        try {
            observation = browser.observe(target.toString());
        } catch (BrowserWorkerException ex) {
            BrowserJobStatus status = "ORIGIN_BLOCKED".equals(ex.getCode())
                    || "UNAUTHORIZED_TARGET".equals(ex.getCode()) ? BrowserJobStatus.DENIED : BrowserJobStatus.FAILED;
            jobs.record(ownerId, origin, status, null, ex.getCode());
            prepare(authentication, model);
            model.addAttribute("browserError", ex.getMessage());
            return "browser/studio";
        }
        jobs.record(ownerId, observation.origin(), BrowserJobStatus.COMPLETED, observation.title(), null);
        prepare(authentication, model);
        model.addAttribute("observation", observation);
        return "browser/studio";
    }

    @PostMapping("/history/{id}/delete")
    public String delete(@PathVariable java.util.UUID id, Authentication authentication, RedirectAttributes redirect) {
        try {
            jobs.delete(actor(authentication), id);
            redirect.addFlashAttribute("browserNotice", "Observation history entry deleted.");
        } catch (BrowserObservationNotFoundException ex) {
            redirect.addFlashAttribute("browserNotice", "That history entry is no longer available.");
        }
        return "redirect:/browser";
    }

    private void prepare(Authentication authentication, Model model) {
        var ownerId = actor(authentication);
        model.addAttribute("activeNav", "browser");
        model.addAttribute("browserConfigured", browser.isConfigured());
        model.addAttribute("browserJobs", jobs.recent(ownerId));
        if (!model.containsAttribute("browserObserveForm")) model.addAttribute("browserObserveForm", new BrowserObserveForm());
    }

    private java.util.UUID actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof TaskOsUserDetails user) return user.getId();
        throw new AccessDeniedException("A TASK OS account is required.");
    }
}
