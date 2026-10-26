package com.taskos.task.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.task.dto.TagForm;
import com.taskos.task.entity.TaskTagColor;
import com.taskos.task.service.DuplicateTaskTagException;
import com.taskos.task.service.TaskRuleException;
import com.taskos.task.service.TaskTagService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class TaskTagController {
    private final TaskTagService tags;

    public TaskTagController(TaskTagService tags) {
        this.tags = tags;
    }

    @GetMapping("/tags")
    public String list(Authentication authentication, Model model) {
        model.addAttribute("tagForm", new TagForm());
        model.addAttribute("activeNav", "tags");
        populate(model, signedIn(authentication).getId());
        return "tasks/tags";
    }

    @PostMapping("/tags")
    public String create(@Valid @ModelAttribute("tagForm") TagForm form, BindingResult binding,
            Authentication authentication, Model model, RedirectAttributes redirect) {
        UUID ownerId = signedIn(authentication).getId();
        if (binding.hasErrors()) {
            model.addAttribute("activeNav", "tags");
            populate(model, ownerId);
            return "tasks/tags";
        }
        try {
            tags.create(ownerId, form);
            redirect.addFlashAttribute("taskMessage", "Tag created.");
            return "redirect:/tags";
        } catch (DuplicateTaskTagException | TaskRuleException ex) {
            binding.reject("tag.invalid", ex.getMessage());
            populate(model, ownerId);
            return "tasks/tags";
        }
    }

    @PostMapping("/tags/{id}")
    public String update(@PathVariable UUID id, @Valid @ModelAttribute("tagForm") TagForm form,
            BindingResult binding, Authentication authentication, RedirectAttributes redirect) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("taskError", "Check the tag name and color.");
            return "redirect:/tags";
        }
        try {
            tags.update(signedIn(authentication).getId(), id, form);
            redirect.addFlashAttribute("taskMessage", "Tag updated.");
        } catch (DuplicateTaskTagException | TaskRuleException ex) {
            redirect.addFlashAttribute("taskError", ex.getMessage());
        }
        return "redirect:/tags";
    }

    @PostMapping("/tags/{id}/delete")
    public String delete(@PathVariable UUID id, Authentication authentication, RedirectAttributes redirect) {
        tags.delete(signedIn(authentication).getId(), id);
        redirect.addFlashAttribute("taskMessage", "Tag deleted. Existing tasks keep their other data.");
        return "redirect:/tags";
    }

    private void populate(Model model, UUID ownerId) {
        model.addAttribute("tags", tags.list(ownerId));
        model.addAttribute("tagColors", TaskTagColor.values());
    }

    private TaskOsUserDetails signedIn(Authentication authentication) {
        return (TaskOsUserDetails) authentication.getPrincipal();
    }
}
