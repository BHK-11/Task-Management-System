package com.taskos.automation.dto;

import com.taskos.automation.entity.AutomationMisfirePolicy;
import com.taskos.automation.entity.AutomationScheduleFrequency;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record AutomationScheduleDefinition(AutomationScheduleFrequency frequency, DayOfWeek dayOfWeek,
        LocalTime localTime, String timezone, AutomationMisfirePolicy misfirePolicy) { }
