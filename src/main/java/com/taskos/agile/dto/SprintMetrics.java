package com.taskos.agile.dto;

public record SprintMetrics(int plannedPoints, int completedPoints, int remainingPoints,
        int completedItems, int remainingItems, int unestimatedItems, int scopeChanges) { }
