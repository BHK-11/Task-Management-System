package com.taskos.web;

import com.taskos.common.FeatureAvailabilityService;
import com.taskos.common.FeatureCard;
import java.util.List;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WorkspacePreviewController {
    private final FeatureAvailabilityService featureAvailability;

    public WorkspacePreviewController(FeatureAvailabilityService featureAvailability) {
        this.featureAvailability = featureAvailability;
    }

    @GetMapping("/preview")
    public String preview(Model model) {
        List<FeatureCard> foundationFeatures = featureAvailability.allFeatures().stream()
                .filter(feature -> feature.category().equals("Foundation"))
                .toList();
        model.addAttribute("foundationFeatures", foundationFeatures);
        return "pages/workspace-preview";
    }

    @GetMapping("/app")
    public String workspace() {
        return "redirect:/dashboard";
    }
}
