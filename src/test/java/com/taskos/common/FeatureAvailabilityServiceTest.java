package com.taskos.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class FeatureAvailabilityServiceTest {
    private final FeatureAvailabilityService service = new FeatureAvailabilityService();

    @Test
    void catalogMarksImplementedFoundationAndTaskManagementAsAvailable() {
        assertEquals(13, service.allFeatures().size());
        assertEquals(12, service.allFeatures().stream()
                .filter(feature -> feature.status() == FeatureStatus.AVAILABLE).count());
        assertTrue(service.allFeatures().stream().anyMatch(feature -> feature.name().equals("Goals, OKRs and habits")
                && feature.status() == FeatureStatus.AVAILABLE));
        assertEquals(1, service.plannedFeatures().size());
        assertEquals("Advanced reports and saved dashboards", service.plannedFeatures().getFirst().name());
        assertTrue(service.plannedFeatures().stream().allMatch(feature -> feature.status() == FeatureStatus.PLANNED));
    }
}
