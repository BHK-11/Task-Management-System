package com.taskos.web;

import com.taskos.common.FeatureAvailabilityService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class RoadmapController {
    private final FeatureAvailabilityService featureAvailability;

    public RoadmapController(FeatureAvailabilityService featureAvailability) {
        this.featureAvailability = featureAvailability;
    }

    @GetMapping("/roadmap")
    public String roadmap(Model model) {
        model.addAttribute("plannedFeatures", featureAvailability.plannedFeatures());
        return "pages/roadmap";
    }
}
