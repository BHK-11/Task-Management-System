package com.taskos.analytics.dto;

public record AnalyticsMetricDefinition(String metricId, String name, String description,
        String source, String formula, String unit, String completeness, String version) { }
