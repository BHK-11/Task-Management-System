package com.taskos.analytics.controller;

import com.taskos.analytics.service.AnalyticsCsvExporter;
import com.taskos.analytics.service.AnalyticsService;
import com.taskos.analytics.dto.AnalyticsOverview;
import com.taskos.auth.service.TaskOsUserDetails;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

@Controller
public class AnalyticsController {
    private final AnalyticsService analytics;
    private final AnalyticsCsvExporter csv;

    public AnalyticsController(AnalyticsService analytics, AnalyticsCsvExporter csv) {
        this.analytics = analytics;
        this.csv = csv;
    }

    @GetMapping("/analytics")
    public String overview(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) UUID projectId, Authentication authentication, Model model) {
        model.addAttribute("analytics", analytics.overview(ownerId(authentication), start, end, projectId));
        model.addAttribute("activeNav", "analytics");
        return "analytics/index";
    }

    @GetMapping("/api/analytics/overview")
    @ResponseBody
    public AnalyticsOverview api(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) UUID projectId, Authentication authentication) {
        return analytics.overview(ownerId(authentication), start, end, projectId);
    }

    @GetMapping(value = "/analytics/export.csv", produces = "text/csv")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) UUID projectId, Authentication authentication) {
        byte[] body = csv.export(analytics.overview(ownerId(authentication), start, end, projectId))
                .getBytes(StandardCharsets.UTF_8);
        return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("task-os-analytics.csv").build().toString())
                .body(body);
    }

    private static UUID ownerId(Authentication authentication) {
        return ((TaskOsUserDetails) authentication.getPrincipal()).getId();
    }
}
