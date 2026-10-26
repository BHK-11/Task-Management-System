package com.taskos.time.service;

import com.taskos.time.entity.MonthEndPolicy;
import com.taskos.time.entity.RecurrenceFrequency;
import com.taskos.time.entity.RecurrenceRule;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class RecurrenceCalculator {
    public LocalDate next(RecurrenceRule rule, LocalDate after) {
        return switch (rule.getFrequency()) {
            case DAILY -> after.plusDays(rule.getIntervalCount());
            case WEEKLY -> nextWeekly(rule, after);
            case MONTHLY -> nextMonthly(rule, after);
            case YEARLY -> nextYearly(rule, after);
        };
    }

    private LocalDate nextWeekly(RecurrenceRule rule, LocalDate after) {
        Set<DayOfWeek> days = Arrays.stream(rule.getWeekdays().split(","))
                .filter(value -> !value.isBlank()).map(Integer::parseInt).map(DayOfWeek::of).collect(Collectors.toSet());
        if (days.isEmpty()) days = Set.of(rule.getAnchorDate().getDayOfWeek());
        LocalDate anchorWeek = rule.getAnchorDate().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        for (LocalDate candidate = after.plusDays(1); candidate.isBefore(after.plusYears(10)); candidate = candidate.plusDays(1)) {
            long weeks = ChronoUnit.WEEKS.between(anchorWeek, candidate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)));
            if (weeks >= 0 && weeks % rule.getIntervalCount() == 0 && days.contains(candidate.getDayOfWeek())) return candidate;
        }
        return null;
    }

    private LocalDate nextMonthly(RecurrenceRule rule, LocalDate after) {
        YearMonth month = YearMonth.from(after).plusMonths(1);
        for (int i = 0; i < 1200; i++, month = month.plusMonths(1)) {
            long months = ChronoUnit.MONTHS.between(YearMonth.from(rule.getAnchorDate()), month);
            if (months < 0 || months % rule.getIntervalCount() != 0) continue;
            int day = rule.getAnchorDate().getDayOfMonth();
            if (day > month.lengthOfMonth()) {
                if (rule.getMonthEndPolicy() == MonthEndPolicy.SKIP) continue;
                day = month.lengthOfMonth();
            }
            LocalDate candidate = month.atDay(day);
            if (candidate.isAfter(after)) return candidate;
        }
        return null;
    }

    private LocalDate nextYearly(RecurrenceRule rule, LocalDate after) {
        for (int year = after.getYear() + 1, i = 1; i <= 1200; year++, i++) {
            long years = year - (long) rule.getAnchorDate().getYear();
            if (years < 0 || years % rule.getIntervalCount() != 0) continue;
            YearMonth month = YearMonth.of(year, rule.getAnchorDate().getMonth());
            int day = rule.getAnchorDate().getDayOfMonth();
            if (day > month.lengthOfMonth()) {
                if (rule.getMonthEndPolicy() == MonthEndPolicy.SKIP) continue;
                day = month.lengthOfMonth();
            }
            LocalDate candidate = month.atDay(day);
            if (candidate.isAfter(after)) return candidate;
        }
        return null;
    }
}
