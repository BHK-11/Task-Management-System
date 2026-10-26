package com.taskos.task.entity;

public enum TaskTagColor {
    SLATE, BLUE, TEAL, AMBER, ROSE, VIOLET;

    public String cssName() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
