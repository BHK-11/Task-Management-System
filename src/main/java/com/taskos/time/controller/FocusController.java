package com.taskos.time.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.task.entity.Task;
import com.taskos.task.repository.TaskRepository;
import com.taskos.task.service.TaskWorkspaceRedirect;
import com.taskos.time.dto.FocusStartForm;
import com.taskos.time.entity.FocusMode;
import com.taskos.time.service.CalendarQueryService;
import com.taskos.time.service.FocusSessionService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
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
public class FocusController {
    private final FocusSessionService focus;
    private final CalendarQueryService calendar;
    private final TaskRepository tasks;
    public FocusController(FocusSessionService focus, CalendarQueryService calendar, TaskRepository tasks) {
        this.focus = focus; this.calendar = calendar; this.tasks = tasks;
    }

    @GetMapping("/focus/pomodoro")
    public String pomodoro(Authentication authentication, Model model, @RequestParam(required = false) UUID taskId) {
        return focusPage(authentication, model, FocusMode.POMODORO, taskId);
    }
    @GetMapping("/focus/deep-work")
    public String deepWork(Authentication authentication, Model model, @RequestParam(required = false) UUID taskId) {
        return focusPage(authentication, model, FocusMode.DEEP_WORK, taskId);
    }
    @GetMapping("/focus/history")
    public String history(Authentication authentication, Model model) {
        UUID owner = owner(authentication); var history = focus.history(owner);
        Map<UUID, String> taskTitles = tasks.findAllByOwner_IdAndIdIn(owner, history.stream().map(s -> s.getTaskId())
                .filter(id -> id != null).collect(Collectors.toSet())).stream().collect(Collectors.toMap(Task::getId, Task::getTitle));
        model.addAttribute("focusHistory", history); model.addAttribute("taskTitles", taskTitles); model.addAttribute("activeNav", "pomodoro");
        return "time/focus-history";
    }

    @PostMapping("/focus/start")
    public String start(@Valid @ModelAttribute("focusStartForm") FocusStartForm form, BindingResult binding,
            Authentication authentication, RedirectAttributes redirect, @RequestParam(defaultValue = "pomodoro") String returnTo,
            @RequestParam(required = false) String workspaceUrl) {
        if (binding.hasErrors()) {
            redirect.addFlashAttribute("timeError", "Check the focus and break durations, then try again.");
            return workspaceUrl == null ? focusPage(returnTo) : TaskWorkspaceRedirect.afterMutation(workspaceUrl, form.getTaskId());
        }
        focus.start(owner(authentication), form); redirect.addFlashAttribute("timeMessage", "Focus session started.");
        if (workspaceUrl != null) return TaskWorkspaceRedirect.afterMutation(workspaceUrl, form.getTaskId());
        return focusPage(returnTo);
    }
    @PostMapping("/focus/sessions/{id}/pause")
    public String pause(@PathVariable UUID id, Authentication authentication, @RequestParam(defaultValue = "pomodoro") String returnTo) {
        focus.pause(owner(authentication), id); return focusPage(returnTo);
    }
    @PostMapping("/focus/sessions/{id}/resume")
    public String resume(@PathVariable UUID id, Authentication authentication, @RequestParam(defaultValue = "pomodoro") String returnTo) {
        focus.resume(owner(authentication), id); return focusPage(returnTo);
    }
    @PostMapping("/focus/sessions/{id}/complete-phase")
    public String completePhase(@PathVariable UUID id, Authentication authentication, @RequestParam(defaultValue = "pomodoro") String returnTo) {
        focus.completePhase(owner(authentication), id); return focusPage(returnTo);
    }
    @PostMapping("/focus/sessions/{id}/skip-break")
    public String skipBreak(@PathVariable UUID id, Authentication authentication, @RequestParam(defaultValue = "pomodoro") String returnTo) {
        focus.skipBreak(owner(authentication), id); return focusPage(returnTo);
    }
    @PostMapping("/focus/sessions/{id}/stop")
    public String stop(@PathVariable UUID id, Authentication authentication, @RequestParam(defaultValue = "pomodoro") String returnTo,
            RedirectAttributes redirect, @RequestParam(required = false) String workspaceUrl) {
        UUID ownerId = owner(authentication);
        var active = focus.active(ownerId);
        UUID taskId = workspaceUrl == null || active == null ? null : active.getTaskId();
        focus.stop(ownerId, id); redirect.addFlashAttribute("timeMessage", "Focus session saved to your history and tracked time.");
        if (workspaceUrl != null && taskId != null) return TaskWorkspaceRedirect.afterMutation(workspaceUrl, taskId);
        return focusPage(returnTo);
    }

    private String focusPage(Authentication authentication, Model model, FocusMode mode, UUID selectedTaskId) {
        UUID owner = owner(authentication); FocusStartForm form = new FocusStartForm(); form.setMode(mode);
        if (mode == FocusMode.DEEP_WORK) form.setFocusMinutes(60);
        List<Task> taskOptions = new java.util.ArrayList<>(calendar.taskOptions(owner));
        if (selectedTaskId != null) {
            Task task = tasks.findDetailByIdAndOwner(selectedTaskId, owner)
                    .orElseThrow(() -> new com.taskos.time.service.TimeRuleException("That task is not available."));
            form.setTaskId(selectedTaskId);
            if (taskOptions.stream().noneMatch(option -> option.getId().equals(selectedTaskId))) taskOptions.add(task);
        }
        var active = focus.active(owner);
        model.addAttribute("focusStartForm", form); model.addAttribute("focusTimer", focus.timer(owner));
        if (active != null && active.getTaskId() != null)
            tasks.findDetailByIdAndOwner(active.getTaskId(), owner).ifPresent(task -> model.addAttribute("focusTaskTitle", task.getTitle()));
        model.addAttribute("taskOptions", taskOptions); model.addAttribute("returnTo", mode == FocusMode.DEEP_WORK ? "deep-work" : "pomodoro");
        model.addAttribute("activeNav", "pomodoro"); return "time/focus";
    }
    private static String focusPage(String page) { return "redirect:/focus/" + ("deep-work".equals(page) ? "deep-work" : "pomodoro"); }
    private static UUID owner(Authentication authentication) { return ((TaskOsUserDetails) authentication.getPrincipal()).getId(); }
}
