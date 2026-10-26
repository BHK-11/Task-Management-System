package com.taskos.agile.dto;

import com.taskos.agile.entity.Epic;

public record EpicProgress(Epic epic, int completedItems, int totalItems,
        int completedPoints, int estimatedPoints, int unestimatedItems) { }
