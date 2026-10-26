package com.taskos.collaboration.dto;

import com.taskos.collaboration.entity.WorkspaceMembership;

public record WorkloadMember(WorkspaceMembership membership, long openTasks, long dueSoon,
        long overdue, long highPriority, boolean overLimit, String signal) { }
