package com.taskos.scheduling.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.collaboration.service.ProjectAccessService;
import com.taskos.collaboration.service.WorkspacePermission;
import com.taskos.scheduling.dto.ScheduleChangePreview;
import com.taskos.scheduling.entity.TaskDependencyType;
import com.taskos.scheduling.service.ProjectScheduleService;
import com.taskos.task.service.TaskRuleException;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@Transactional
@RequestMapping("/projects/{projectId}")
public class ProjectScheduleController {
    private final ProjectScheduleService schedules;
    private final ProjectAccessService projectAccess;

    public ProjectScheduleController(ProjectScheduleService schedules, ProjectAccessService projectAccess) {
        this.schedules = schedules; this.projectAccess = projectAccess;
    }

    @GetMapping("/gantt")
    public String gantt(@PathVariable UUID projectId, @RequestParam(required = false) UUID baseline,
            Authentication authentication, Model model) {
        UUID actorId = userId(authentication);
        return render(projectId, "gantt", baseline, null, null, actorId,
                projectOwner(authentication, projectId, false), model);
    }

    @GetMapping("/dependencies")
    public String dependencies(@PathVariable UUID projectId, Authentication authentication, Model model) {
        UUID actorId = userId(authentication);
        return render(projectId, "dependencies", null, null, null, actorId,
                projectOwner(authentication, projectId, false), model);
    }

    @PostMapping("/scheduling/dependencies")
    public String addDependency(@PathVariable UUID projectId, @RequestParam UUID predecessorTaskId,
            @RequestParam UUID successorTaskId, @RequestParam TaskDependencyType type,
            @RequestParam(defaultValue = "0") @Min(0) @Max(36500) int lagDays,
            Authentication authentication, RedirectAttributes redirect) {
        try {
            schedules.addDependency(projectOwner(authentication, projectId, true), projectId, predecessorTaskId, successorTaskId, type, lagDays);
            redirect.addFlashAttribute("scheduleNotice", "Dependency saved.");
        } catch (TaskRuleException exception) {
            redirect.addFlashAttribute("scheduleError", exception.getMessage());
        }
        return "redirect:/projects/" + projectId + "/dependencies";
    }

    @PostMapping("/scheduling/dependencies/{dependencyId}/delete")
    public String removeDependency(@PathVariable UUID projectId, @PathVariable UUID dependencyId,
            Authentication authentication, RedirectAttributes redirect) {
        try {
            schedules.removeDependency(projectOwner(authentication, projectId, true), projectId, dependencyId);
            redirect.addFlashAttribute("scheduleNotice", "Dependency removed.");
        } catch (TaskRuleException exception) {
            redirect.addFlashAttribute("scheduleError", exception.getMessage());
        }
        return "redirect:/projects/" + projectId + "/dependencies";
    }

    @PostMapping("/scheduling/tasks/{taskId}/preview")
    public String preview(@PathVariable UUID projectId, @PathVariable UUID taskId,
            @RequestParam @Min(1) @Max(36500) int durationDays,
            @RequestParam(required = false) LocalDate plannedStart,
            @RequestParam long projectVersion, @RequestParam long taskVersion,
            Authentication authentication, Model model) {
        UUID ownerId = projectOwner(authentication, projectId, true);
        try {
            ScheduleChangePreview preview = schedules.preview(ownerId, projectId, taskId, durationDays,
                    plannedStart, projectVersion, taskVersion);
            return render(projectId, "gantt", null, preview, null, userId(authentication), ownerId, model);
        } catch (TaskRuleException exception) {
            return render(projectId, "gantt", null, null, exception.getMessage(), userId(authentication), ownerId, model);
        }
    }

    @PostMapping("/scheduling/tasks/{taskId}/apply")
    public String apply(@PathVariable UUID projectId, @PathVariable UUID taskId,
            @RequestParam @Min(1) @Max(36500) int durationDays,
            @RequestParam(required = false) LocalDate plannedStart,
            @RequestParam long projectVersion, @RequestParam long taskVersion,
            Authentication authentication, RedirectAttributes redirect) {
        try {
            schedules.apply(projectOwner(authentication, projectId, true), projectId, taskId, durationDays, plannedStart,
                    projectVersion, taskVersion);
            redirect.addFlashAttribute("scheduleNotice", "Schedule inputs saved and recalculated.");
        } catch (TaskRuleException exception) {
            redirect.addFlashAttribute("scheduleError", exception.getMessage());
        }
        return "redirect:/projects/" + projectId + "/gantt";
    }

    @PostMapping("/scheduling/baselines")
    public String createBaseline(@PathVariable UUID projectId, @RequestParam String name,
            Authentication authentication, RedirectAttributes redirect) {
        try {
            UUID baselineId = schedules.createBaseline(projectOwner(authentication, projectId, true), projectId, name);
            redirect.addFlashAttribute("scheduleNotice", "Schedule baseline saved.");
            return "redirect:/projects/" + projectId + "/gantt?baseline=" + baselineId;
        } catch (TaskRuleException exception) {
            redirect.addFlashAttribute("scheduleError", exception.getMessage());
            return "redirect:/projects/" + projectId + "/gantt";
        }
    }

    private String render(UUID projectId, String view, UUID baseline, ScheduleChangePreview preview,
            String error, UUID actorId, UUID ownerId, Model model) {
        model.addAttribute("schedulePage", schedules.page(ownerId, projectId, baseline, preview,
                (String) model.asMap().get("scheduleNotice")));
        model.addAttribute("scheduleView", true);
        model.addAttribute("scheduleSection", view);
        model.addAttribute("scheduleError", error != null ? error : model.asMap().get("scheduleError"));
        model.addAttribute("activeNav", "projects");
        model.addAttribute("canEditProject", projectAccess.canEdit(actorId, projectId));
        return "projects/schedule";
    }

    private UUID projectOwner(Authentication authentication, UUID projectId, boolean write) {
        return projectAccess.require(userId(authentication), projectId,
                write ? WorkspacePermission.EDIT_TASK : WorkspacePermission.VIEW).getOwner().getId();
    }

    private UUID userId(Authentication authentication) { return ((TaskOsUserDetails) authentication.getPrincipal()).getId(); }
}
