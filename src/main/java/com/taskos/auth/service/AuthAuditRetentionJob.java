package com.taskos.auth.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class AuthAuditRetentionJob {
    private final AuthAuditService auditService;

    public AuthAuditRetentionJob(AuthAuditService auditService) {
        this.auditService = auditService;
    }

    @Scheduled(cron = "0 20 3 * * *", zone = "UTC")
    public void removeExpiredEvents() {
        auditService.purgeOlderThan(Instant.now().minus(90, ChronoUnit.DAYS));
    }
}
