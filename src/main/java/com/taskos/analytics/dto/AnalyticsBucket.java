package com.taskos.analytics.dto;

public record AnalyticsBucket(String label, long count, int percentage, String href) { }
