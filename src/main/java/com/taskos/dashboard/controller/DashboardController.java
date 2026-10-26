package com.taskos.dashboard.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.dashboard.service.DashboardService;
import com.taskos.task.dto.TaskForm;
import com.taskos.task.entity.TaskPriority;
import com.taskos.productivity.service.ProductivityService;
import com.taskos.project.service.ProjectService;
import com.taskos.time.service.CalendarQueryService;
import com.taskos.time.service.TimeZoneResolver;
import com.taskos.goals.service.GoalService;
import com.taskos.habits.service.HabitService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class DashboardController {
    private final DashboardService dashboard;
    private final ProductivityService productivity;
    private final ProjectService projects;
    private final CalendarQueryService calendar;
    private final TimeZoneResolver zones;
    private final Clock clock;
    private final GoalService goals;
    private final HabitService habits;

    public DashboardController(DashboardService dashboard, ProductivityService productivity, ProjectService projects,
            CalendarQueryService calendar, TimeZoneResolver zones, Clock clock, GoalService goals, HabitService habits) {
        this.dashboard = dashboard;
        this.productivity = productivity;
        this.projects = projects;
        this.calendar = calendar;
        this.zones = zones;
        this.clock = clock;
        this.goals = goals;
        this.habits = habits;
    }

    @GetMapping("/dashboard")
    public String overview(Authentication authentication, Model model) {
        UUID owner = ownerId(authentication);
        model.addAttribute("overview", dashboard.overview(owner));
        model.addAttribute("todaySchedule", calendar.page(owner, "day", LocalDate.now(clock.withZone(zones.zone(owner)))));
        var planDate = productivity.today(owner);
        model.addAttribute("productivityInboxCount", productivity.openInboxCount(owner));
        model.addAttribute("productivityPlan", productivity.dailyPlan(owner, planDate));
        model.addAttribute("productivityPlanEntries", productivity.planEntries(owner, planDate));
        model.addAttribute("activeProjectCount", projects.activeProjectCount(owner));
        model.addAttribute("activeGoalCount", goals.activeCount(owner));
        model.addAttribute("activeHabitCount", habits.activeCount(owner));
        model.addAttribute("taskForm", new TaskForm());
        model.addAttribute("priorities", TaskPriority.values());
        model.addAttribute("activeNav", "dashboard");
        return "dashboard/index";
    }

    @GetMapping("/my-day")
    public String myDay(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        model.addAttribute("taskPage", dashboard.myDay(ownerId(authentication), page));
        model.addAttribute("activeNav", "my-day");
        model.addAttribute("returnTo", "my-day");
        return "dashboard/task-list";
    }

    @GetMapping("/tasks/today")
    public String today(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        model.addAttribute("taskPage", dashboard.today(ownerId(authentication), page));
        model.addAttribute("activeNav", "today");
        model.addAttribute("returnTo", "today");
        return "dashboard/task-list";
    }

    @GetMapping("/tasks/tomorrow")
    public String tomorrow(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        model.addAttribute("taskPage", dashboard.tomorrow(ownerId(authentication), page));
        model.addAttribute("activeNav", "tomorrow");
        model.addAttribute("returnTo", "tomorrow");
        return "dashboard/task-list";
    }

    @GetMapping("/tasks/overdue")
    public String overdue(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        model.addAttribute("taskPage", dashboard.overdue(ownerId(authentication), page));
        model.addAttribute("activeNav", "overdue");
        model.addAttribute("returnTo", "overdue");
        return "dashboard/task-list";
    }

    @GetMapping("/tasks/upcoming")
    public String upcoming(@RequestParam(defaultValue = "0") int page, Authentication authentication, Model model) {
        model.addAttribute("upcomingPage", dashboard.upcoming(ownerId(authentication), page));
        model.addAttribute("activeNav", "upcoming");
        model.addAttribute("returnTo", "upcoming");
        return "dashboard/upcoming";
    }

    private UUID ownerId(Authentication authentication) {
        return ((TaskOsUserDetails) authentication.getPrincipal()).getId();
    }
}
