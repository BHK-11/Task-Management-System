package com.taskos.time.dto;

import com.taskos.time.entity.TimeEntry;

public record TimeEntryView(TimeEntry entry, String taskTitle, long seconds) { }
