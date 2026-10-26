package com.taskos.task.service;

import java.net.URI;
import java.util.UUID;

public final class TaskWorkspaceRedirect {
    private TaskWorkspaceRedirect() { }

    public static String afterMutation(String requested, UUID taskId) {
        String fallback = taskId == null ? "/tasks" : "/tasks/" + taskId;
        if (requested == null || requested.length() > 4096) return "redirect:" + fallback;
        try {
            URI uri = URI.create(requested);
            if (!uri.isAbsolute() && uri.getRawAuthority() == null && uri.getRawFragment() == null
                    && "/tasks".equals(uri.getPath())) {
                return "redirect:" + uri.toASCIIString();
            }
        } catch (IllegalArgumentException ignored) {
            // Invalid return targets fall back to the selected task.
        }
        return "redirect:" + fallback;
    }
}
