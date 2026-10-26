package com.taskos.ai.controller;

import com.taskos.ai.dto.AiPlanForm;
import com.taskos.ai.service.AiCopilotException;
import com.taskos.ai.service.AiArtifactStorageService;
import com.taskos.ai.service.AiPlanService;
import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.task.service.TaskService;
import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/ai")
public class AiCopilotController {
    private final AiPlanService plans;
    private final TaskService tasks;
    private final AiArtifactStorageService artifacts;

    public AiCopilotController(AiPlanService plans, TaskService tasks, AiArtifactStorageService artifacts) {
        this.plans = plans; this.tasks = tasks; this.artifacts = artifacts;
    }

    @GetMapping
    public String index(@RequestParam(required = false) UUID planId, @RequestParam(required = false) UUID taskId,
            Authentication authentication, Model model) {
        prepare(model, authentication, planId, taskId);
        return "ai/copilot";
    }

    @PostMapping("/plans")
    public String plan(@Valid @ModelAttribute("planForm") AiPlanForm form, BindingResult binding,
            Authentication authentication, Model model, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            prepare(model, authentication, null, null);
            model.addAttribute("formError", "Review the instruction and try again.");
            return "ai/copilot";
        }
        try {
            var saved = plans.planWork(actor(authentication), form.getInstruction());
            return "redirect:/ai?planId=" + saved.getId();
        } catch (AiCopilotException ex) {
            redirect.addFlashAttribute("copilotError", ex.getMessage());
            return "redirect:/ai";
        }
    }

    @PostMapping("/plans/{id}/edit-work")
    public String editWork(@PathVariable UUID id, @RequestParam List<UUID> stepIds,
            @RequestParam List<String> titles, @RequestParam List<String> descriptions,
            @RequestParam List<String> priorities, @RequestParam List<String> dueDates,
            @RequestParam List<String> contents, Authentication authentication, RedirectAttributes redirect) {
        try {
            plans.editWork(actor(authentication), id, stepIds, titles, descriptions, priorities, dueDates, contents);
            redirect.addFlashAttribute("copilotMessage", "Plan edits saved. Review them before approving.");
        } catch (AiCopilotException ex) {
            redirect.addFlashAttribute("copilotError", ex.getMessage());
        }
        return "redirect:/ai?planId=" + id;
    }

    @PostMapping("/plans/{id}/edit")
    public String edit(@PathVariable UUID id, @RequestParam List<UUID> stepIds,
            @RequestParam List<String> titles, @RequestParam List<String> descriptions,
            @RequestParam List<String> priorities, @RequestParam List<String> dueDates,
            Authentication authentication, RedirectAttributes redirect) {
        try {
            plans.edit(actor(authentication), id, stepIds, titles, descriptions, priorities, dueDates);
            redirect.addFlashAttribute("copilotMessage", "Plan edits saved. Review it before approving.");
        } catch (AiCopilotException ex) {
            redirect.addFlashAttribute("copilotError", ex.getMessage());
        }
        return "redirect:/ai?planId=" + id;
    }

    @PostMapping("/plans/{id}/approve")
    public String approve(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        try {
            plans.approve(actor(authentication), id);
            redirect.addFlashAttribute("copilotMessage", "Approved. Work is queued; this page will show verified results as they finish.");
        } catch (AiCopilotException ex) {
            redirect.addFlashAttribute("copilotError", ex.getMessage());
        }
        return "redirect:/ai?planId=" + id;
    }

    @GetMapping("/artifacts/{id}")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, Authentication authentication) {
        var file = artifacts.load(actor(authentication), id);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.bytes());
    }

    @PostMapping("/plans/{id}/reject")
    public String reject(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        try {
            plans.reject(actor(authentication), id);
            redirect.addFlashAttribute("copilotMessage", "Plan rejected. No tasks were created.");
        } catch (AiCopilotException ex) {
            redirect.addFlashAttribute("copilotError", ex.getMessage());
        }
        return "redirect:/ai?planId=" + id;
    }

    private void prepare(Model model, Authentication authentication, UUID planId, UUID taskId) {
        UUID ownerId = actor(authentication);
        model.addAttribute("aiConfigured", plans.isConfigured());
        model.addAttribute("aiPlans", plans.history(ownerId));
        model.addAttribute("selectedPlan", planId == null ? null : plans.detail(ownerId, planId));
        if (!model.containsAttribute("planForm")) {
            AiPlanForm form = new AiPlanForm();
            if (taskId != null) {
                var task = tasks.detail(ownerId, taskId).task();
                String instruction = "Help me plan the next steps for this task.\nTask: " + task.getTitle()
                        + (task.getDescription().isBlank() ? "" : "\nDetails: " + task.getDescription());
                form.setInstruction(instruction.length() > 4000 ? instruction.substring(0, 4000) : instruction);
                model.addAttribute("assistTaskTitle", task.getTitle());
            }
            model.addAttribute("planForm", form);
        }
        model.addAttribute("activeNav", "ai");
    }

    private UUID actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof TaskOsUserDetails user) return user.getId();
        throw new AccessDeniedException("A TASK OS account is required.");
    }
}
