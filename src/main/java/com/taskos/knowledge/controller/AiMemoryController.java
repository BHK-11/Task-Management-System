package com.taskos.knowledge.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.knowledge.dto.AiMemoryForm;
import com.taskos.knowledge.entity.AiMemoryCategory;
import com.taskos.knowledge.service.AiMemoryConsentRequiredException;
import com.taskos.knowledge.service.AiMemoryNotFoundException;
import com.taskos.knowledge.service.AiMemoryService;
import jakarta.validation.Valid;
import java.util.Arrays;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/settings/memory")
public class AiMemoryController {
    private final AiMemoryService memories;

    public AiMemoryController(AiMemoryService memories) { this.memories = memories; }

    @GetMapping
    public String show(Authentication authentication, Model model) {
        UUID ownerId = actor(authentication);
        model.addAttribute("preference", memories.preference(ownerId));
        model.addAttribute("memories", memories.list(ownerId));
        model.addAttribute("memoryForm", new AiMemoryForm());
        model.addAttribute("categories", Arrays.stream(AiMemoryCategory.values()).toList());
        model.addAttribute("activeNav", "memory");
        return "knowledge/memory-settings";
    }

    @PostMapping("/consent")
    public String consent(Authentication authentication, @RequestParam boolean enabled, RedirectAttributes redirect) {
        memories.setEnabled(actor(authentication), enabled);
        redirect.addFlashAttribute("memoryNotice", enabled
                ? "Persistent memory is on. Only details you add here can be recalled."
                : "Persistent memory is off. Your saved items remain available here for review or deletion.");
        return "redirect:/settings/memory";
    }

    @PostMapping
    public String add(Authentication authentication, @Valid @ModelAttribute("memoryForm") AiMemoryForm form,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        UUID ownerId = actor(authentication);
        AiMemoryCategory category = parseCategory(form.getCategory(), binding);
        if (binding.hasErrors()) return showWithForm(ownerId, form, model);
        try {
            memories.add(ownerId, category, form.getContent());
        } catch (IllegalArgumentException | AiMemoryConsentRequiredException ex) {
            binding.rejectValue("content", "memory.invalid", ex.getMessage());
            return showWithForm(ownerId, form, model);
        }
        redirect.addFlashAttribute("memoryNotice", "Confirmed memory saved.");
        return "redirect:/settings/memory";
    }

    @PostMapping("/{id}/correct")
    public String correct(Authentication authentication, @PathVariable UUID id,
            @Valid @ModelAttribute("memoryForm") AiMemoryForm form, BindingResult binding,
            Model model, RedirectAttributes redirect) {
        UUID ownerId = actor(authentication);
        AiMemoryCategory category = parseCategory(form.getCategory(), binding);
        if (binding.hasErrors()) return showWithForm(ownerId, form, model);
        try {
            memories.correct(ownerId, id, category, form.getContent());
        } catch (IllegalArgumentException | AiMemoryConsentRequiredException ex) {
            binding.rejectValue("content", "memory.invalid", ex.getMessage());
            return showWithForm(ownerId, form, model);
        }
        redirect.addFlashAttribute("memoryNotice", "Memory corrected; the previous version is superseded.");
        return "redirect:/settings/memory";
    }

    @PostMapping("/{id}/delete")
    public String delete(Authentication authentication, @PathVariable UUID id, RedirectAttributes redirect) {
        try {
            memories.delete(actor(authentication), id);
        } catch (AiMemoryNotFoundException ex) {
            redirect.addFlashAttribute("memoryNotice", "That memory is no longer available.");
            return "redirect:/settings/memory";
        }
        redirect.addFlashAttribute("memoryNotice", "Memory and its superseded versions deleted.");
        return "redirect:/settings/memory";
    }

    private String showWithForm(UUID ownerId, AiMemoryForm form, Model model) {
        model.addAttribute("preference", memories.preference(ownerId));
        model.addAttribute("memories", memories.list(ownerId));
        model.addAttribute("memoryForm", form);
        model.addAttribute("categories", Arrays.stream(AiMemoryCategory.values()).toList());
        model.addAttribute("activeNav", "memory");
        return "knowledge/memory-settings";
    }

    private AiMemoryCategory parseCategory(String category, BindingResult binding) {
        try { return AiMemoryCategory.valueOf(category); }
        catch (RuntimeException ex) {
            binding.rejectValue("category", "memory.category", "Choose a listed memory category.");
            return AiMemoryCategory.PREFERENCE;
        }
    }

    private UUID actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof TaskOsUserDetails user) return user.getId();
        throw new AccessDeniedException("A TASK OS account is required.");
    }
}
