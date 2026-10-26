package com.taskos.time.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.taskos.time.entity.MonthEndPolicy;
import com.taskos.time.entity.RecurrenceFrequency;
import com.taskos.time.entity.RecurrenceRule;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CalendarTimeRulesTest {
    private final RecurrenceCalculator recurrence = new RecurrenceCalculator();

    @Test
    void monthlyRulesHandleShortMonthsAccordingToTheSelectedPolicy() {
        var anchor = LocalDate.of(2025, 1, 31);
        assertEquals(LocalDate.of(2025, 3, 31), recurrence.next(rule(anchor, RecurrenceFrequency.MONTHLY,
                MonthEndPolicy.SKIP), anchor));
        assertEquals(LocalDate.of(2025, 2, 28), recurrence.next(rule(anchor, RecurrenceFrequency.MONTHLY,
                MonthEndPolicy.LAST_DAY), anchor));
    }

    @Test
    void weeklyRulesFollowSelectedIsoWeekdaysAndInterval() {
        var anchor = LocalDate.of(2025, 1, 6);
        var rule = RecurrenceRule.create(UUID.randomUUID(), UUID.randomUUID(), RecurrenceFrequency.WEEKLY, 2,
                "UTC", anchor, LocalTime.NOON, "1,3", MonthEndPolicy.LAST_DAY, null, null, java.time.Instant.EPOCH);
        assertEquals(LocalDate.of(2025, 1, 8), recurrence.next(rule, anchor));
        assertEquals(LocalDate.of(2025, 1, 20), recurrence.next(rule, LocalDate.of(2025, 1, 8)));
    }

    @Test
    void localScheduleTimesResolveDstGapsAndRepeatedHoursPredictably() {
        ZoneId london = ZoneId.of("Europe/London");
        var gap = LocalTimePolicy.resolve(LocalDateTime.of(2026, 3, 29, 1, 30), london);
        var overlap = LocalTimePolicy.resolve(LocalDateTime.of(2026, 10, 25, 1, 30), london);
        assertEquals(LocalDateTime.of(2026, 3, 29, 2, 30), gap.toLocalDateTime());
        assertEquals(ZoneOffset.ofHours(1), overlap.getOffset());
    }

    private static RecurrenceRule rule(LocalDate anchor, RecurrenceFrequency frequency, MonthEndPolicy policy) {
        return RecurrenceRule.create(UUID.randomUUID(), UUID.randomUUID(), frequency, 1, "UTC", anchor,
                LocalTime.NOON, "", policy, null, null, java.time.Instant.EPOCH);
    }
}
