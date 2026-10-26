package com.taskos.ai.controller;

import com.taskos.ai.service.AiPlanService;
import com.taskos.auth.service.TaskOsUserDetails;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class AgentManagementController {
    private final AiPlanService plans;

    public AgentManagementController(AiPlanService plans) { this.plans = plans; }

    @GetMapping("/agents")
    public String index(Authentication authentication, Model model) {
        if (authentication == null || !(authentication.getPrincipal() instanceof TaskOsUserDetails user)) {
            throw new AccessDeniedException("A TASK OS account is required.");
        }
        model.addAttribute("aiConfigured", plans.isConfigured());
        model.addAttribute("aiPlans", plans.history(user.getId()));
        model.addAttribute("activeNav", "agents");
        return "agents/index";
    }
}
