package com.taskos.browser.dto;

import java.time.Instant;
import java.util.List;

public record BrowserObservation(String origin, String sourceUrl, String title, List<String> headings,
        String visibleText, Instant observedAt, int blockedRequestCount) {}
