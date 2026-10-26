package com.taskos.automation.service;

import com.taskos.automation.dto.AutomationScheduleDefinition;
import com.taskos.automation.entity.AutomationScheduleFrequency;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import org.springframework.stereotype.Component;

@Component
public class AutomationScheduleCalculator {
    public Instant next(AutomationScheduleDefinition schedule, Instant after) {
        var zone = java.time.ZoneId.of(schedule.timezone());
        ZonedDateTime localAfter = after.atZone(zone);
        int searchDays = schedule.frequency() == AutomationScheduleFrequency.DAILY ? 2 : 8;
        for (int offset = 0; offset <= searchDays; offset++) {
            LocalDate date = localAfter.toLocalDate().plusDays(offset);
            if (schedule.frequency() == AutomationScheduleFrequency.WEEKLY && date.getDayOfWeek() != schedule.dayOfWeek()) continue;
            // atZone moves a nonexistent DST wall time forward and selects the earlier offset in an overlap.
            Instant candidate = date.atTime(schedule.localTime()).atZone(zone).toInstant();
            if (candidate.isAfter(after)) return candidate;
        }
        throw new IllegalArgumentException("No future automation run could be calculated.");
    }

    public boolean isMissedByMoreThanOneInterval(AutomationScheduleDefinition schedule, Instant scheduledAt, Instant now) {
        java.time.Duration limit = schedule.frequency() == AutomationScheduleFrequency.DAILY
                ? java.time.Duration.ofDays(1) : java.time.Duration.ofDays(7);
        return now.isAfter(scheduledAt.plus(limit));
    }
}
