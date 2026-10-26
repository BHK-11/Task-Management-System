package com.taskos.time.controller;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.time.service.TimeInsightsService;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class TimeInsightsController {
    private final TimeInsightsService insights;
    public TimeInsightsController(TimeInsightsService insights) { this.insights = insights; }
    @GetMapping("/time/insights")
    public String page(Authentication authentication, Model model) {
        UUID owner = ((TaskOsUserDetails) authentication.getPrincipal()).getId();
        model.addAttribute("timeInsights", insights.page(owner)); model.addAttribute("activeNav", "insights"); return "time/insights";
    }
}
