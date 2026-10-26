package com.taskos.time.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class RecurrenceScheduler {
    private static final Logger log = LoggerFactory.getLogger(RecurrenceScheduler.class);
    private final RecurrenceService recurrences;
    public RecurrenceScheduler(RecurrenceService recurrences) { this.recurrences = recurrences; }
    @Scheduled(fixedDelay = 60_000)
    public void generateDueOccurrences() {
        try { recurrences.processDue(); }
        catch (RuntimeException ex) { log.error("Recurring task generation failed; due rules will be retried on the next poll.", ex); }
    }
}
