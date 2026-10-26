package com.taskos.ai.controller;

import com.taskos.ai.dto.AiModelCatalog;
import com.taskos.ai.dto.AiModelMode;
import com.taskos.ai.dto.AiModelPreferencesForm;
import com.taskos.ai.service.AiModelPreferenceService;
import com.taskos.ai.service.AiPlanService;
import com.taskos.ai.service.AiPlannerClient;
import com.taskos.auth.service.TaskOsUserDetails;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import jakarta.validation.Valid;

@Controller
@RequestMapping("/settings/ai")
public class AiModelSettingsController {
    private final AiModelPreferenceService preferences;
    private final AiPlanService plans;
    private final AiPlannerClient planner;

    public AiModelSettingsController(AiModelPreferenceService preferences, AiPlanService plans, AiPlannerClient planner) {
        this.preferences = preferences;
        this.plans = plans;
        this.planner = planner;
    }

    @GetMapping
    public String show(Authentication authentication, Model model) {
        AiModelPreferencesForm form = new AiModelPreferencesForm();
        var saved = preferences.get(actor(authentication));
        form.setMode(saved.mode().name());
        form.setTarget(saved.providerId() == null ? "" : saved.providerId() + "/" + saved.modelId());
        form.setAllowCloudProcessing(saved.allowCloudProcessing());
        form.setAllowFallback(saved.allowFallback());
        model.addAttribute("preferencesForm", form);
        model.addAttribute("usage", plans.usageThisMonth(actor(authentication)));
        prepare(model, planner.catalog(), form.getTarget());
        return "account/ai-model-settings";
    }

    @PostMapping
    public String save(Authentication authentication, @Valid @ModelAttribute("preferencesForm") AiModelPreferencesForm form,
            BindingResult binding, Model model, RedirectAttributes redirect) {
        UUID ownerId = actor(authentication);
        if (binding.hasErrors()) {
            prepare(model, planner.catalog(), form.getTarget());
            return "account/ai-model-settings";
        }
        try {
            AiModelMode mode = AiModelMode.valueOf(form.getMode());
            String provider = null, selectedModel = null;
            if (mode == AiModelMode.MANUAL) {
                String target = form.getTarget() == null ? "" : form.getTarget().strip();
                int separator = target.indexOf('/');
                if (separator < 1 || separator == target.length() - 1) throw new IllegalArgumentException("Select an available model for manual mode.");
                provider = target.substring(0, separator);
                selectedModel = target.substring(separator + 1);
            }
            preferences.save(ownerId, mode, provider, selectedModel, form.isAllowCloudProcessing(), form.isAllowFallback());
        } catch (IllegalArgumentException ex) {
            binding.rejectValue("target", "ai.preferences.invalid", ex.getMessage());
            prepare(model, planner.catalog(), form.getTarget());
            return "account/ai-model-settings";
        }
        redirect.addFlashAttribute("aiPreferenceNotice", "AI routing preferences saved.");
        return "redirect:/settings/ai";
    }

    private void prepare(Model model, AiModelCatalog catalog, String selectedTarget) {
        model.addAttribute("modes", AiModelMode.values());
        model.addAttribute("modelCatalog", catalog);
        List<TargetOption> targets = new ArrayList<>();
        if (catalog != null && catalog.providers() != null) {
            for (AiModelCatalog.Provider provider : catalog.providers()) {
                if (provider == null || provider.models() == null) continue;
                for (AiModelCatalog.Model available : provider.models()) {
                    if (available != null && available.id() != null)
                        targets.add(new TargetOption(provider.id() + "/" + available.id(), provider.id() + " · " + available.id()));
                }
            }
        }
        model.addAttribute("modelTargets", targets);
        model.addAttribute("savedTargetMissing", selectedTarget != null && !selectedTarget.isBlank()
                && targets.stream().noneMatch(target -> target.value().equals(selectedTarget)));
        model.addAttribute("savedTarget", selectedTarget);
        model.addAttribute("activeNav", "ai");
    }

    private UUID actor(Authentication authentication) {
        if (authentication != null && authentication.getPrincipal() instanceof TaskOsUserDetails user) return user.getId();
        throw new AccessDeniedException("A TASK OS account is required.");
    }

    public record TargetOption(String value, String label) {}
}
