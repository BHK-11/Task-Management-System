package com.taskos.scheduling.service;

import java.util.List;
import java.util.UUID;

public class DependencyCycleException extends IllegalArgumentException {
    private final List<UUID> involvedTaskIds;

    public DependencyCycleException(List<UUID> involvedTaskIds) {
        super("This dependency would create a circular schedule involving " + involvedTaskIds.size() + " task(s).");
        this.involvedTaskIds = List.copyOf(involvedTaskIds);
    }

    public List<UUID> getInvolvedTaskIds() { return involvedTaskIds; }
}
