package com.taskos.user.dto;

import java.time.Instant;
import java.util.UUID;

import com.taskos.user.entity.AccountStatus;
import com.taskos.user.entity.ThemePreference;

public record ProfileView(UUID id, String displayName, String email, AccountStatus status,
        ThemePreference themePreference, String timezone, String locale, Instant createdAt, Instant lastLoginAt) {
}
