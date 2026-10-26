package com.taskos.collaboration.dto;

import com.taskos.collaboration.entity.Workspace;
import com.taskos.task.entity.Task;

public record AssignedTask(Workspace workspace, Task task, String projectName) { }
