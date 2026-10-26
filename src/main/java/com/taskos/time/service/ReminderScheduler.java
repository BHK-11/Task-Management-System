package com.taskos.time.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ReminderScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);
    private final ReminderService reminders;
    public ReminderScheduler(ReminderService reminders) { this.reminders = reminders; }
    @Scheduled(fixedDelay = 30_000)
    public void deliverDueReminders() {
        try { reminders.processDue(); }
        catch (RuntimeException ex) { log.error("In-app reminder delivery failed; pending reminders will be retried.", ex); }
    }
}
