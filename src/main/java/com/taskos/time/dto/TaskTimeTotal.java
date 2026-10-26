package com.taskos.time.dto;

import java.util.UUID;

public record TaskTimeTotal(UUID taskId, String taskTitle, long seconds, String projectName) { }
