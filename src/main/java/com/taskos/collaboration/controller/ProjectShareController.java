package com.taskos.collaboration.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.collaboration.dto.ProjectShareReview;
import com.taskos.collaboration.service.WorkspaceProjectService;
import com.taskos.task.service.TaskRuleException;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class ProjectShareController {
    private final WorkspaceProjectService projects;

    public ProjectShareController(WorkspaceProjectService projects) { this.projects = projects; }

    @GetMapping("/projects/{projectId}/share")
    public String review(@PathVariable UUID projectId, @RequestParam UUID workspaceId,
            Authentication auth, Model model) {
        ProjectShareReview review = projects.review(userId(auth), workspaceId, projectId);
        model.addAttribute("review", review); model.addAttribute("workspaceId", workspaceId);
        model.addAttribute("activeNav", "teams"); model.addAttribute("pageTitle", "Review project sharing — TASK OS");
        return "teams/share-project";
    }

    @PostMapping("/projects/{projectId}/share")
    public String share(@PathVariable UUID projectId, @RequestParam UUID workspaceId,
            @RequestParam(defaultValue = "false") boolean confirmed, Authentication auth, RedirectAttributes redirect) {
        try {
            projects.share(userId(auth), workspaceId, projectId, confirmed);
            redirect.addFlashAttribute("taskMessage", "Project and eligible tasks are now shared with this workspace.");
        } catch (TaskRuleException ex) { redirect.addFlashAttribute("taskError", ex.getMessage()); }
        return "redirect:/workspaces/" + workspaceId;
    }

    private static UUID userId(Authentication auth) { return ((TaskOsUserDetails) auth.getPrincipal()).getId(); }
}
