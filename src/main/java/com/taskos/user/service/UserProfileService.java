package com.taskos.user.service;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Set;
import java.util.UUID;

import com.taskos.auth.entity.AuthEventType;
import com.taskos.auth.service.AuthAuditService;
import com.taskos.user.dto.ProfileView;
import com.taskos.user.entity.ThemePreference;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserProfileService {
    private static final Set<String> SUPPORTED_TIMEZONES = Set.of(
            "UTC", "Asia/Kolkata", "America/Los_Angeles", "America/New_York", "Europe/London");

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthAuditService auditService;
    private final SessionRegistry sessionRegistry;

    public UserProfileService(UserAccountRepository users, PasswordEncoder passwordEncoder,
            AuthAuditService auditService, SessionRegistry sessionRegistry) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.sessionRegistry = sessionRegistry;
    }

    @Transactional(readOnly = true)
    public ProfileView profile(UUID userId) {
        UserAccount user = findUser(userId);
        return new ProfileView(user.getId(), user.getDisplayName(), user.getEmail(), user.getAccountStatus(),
                user.getThemePreference(), user.getTimezone(), user.getLocale(), user.getCreatedAt(), user.getLastLoginAt());
    }

    @Transactional(readOnly = true)
    public ThemePreference themePreference(UUID userId) {
        return findUser(userId).getThemePreference();
    }

    @Transactional
    public void updateDisplayName(UUID userId, String displayName) {
        UserAccount user = findUser(userId);
        user.updateDisplayName(displayName.strip().replaceAll("\\s+", " "), Instant.now());
        auditService.record(AuthEventType.PROFILE_UPDATED, userId);
    }

    @Transactional
    public void updatePreferences(UUID userId, String displayName, ThemePreference theme, String timezone, String locale) {
        if (!SUPPORTED_TIMEZONES.contains(timezone)) {
            throw new IllegalArgumentException("Choose a supported timezone.");
        }
        ZoneId.of(timezone);
        if (!"en".equals(locale)) {
            throw new IllegalArgumentException("English is the only language currently available.");
        }
        UserAccount user = findUser(userId);
        user.updateDisplayName(displayName.strip().replaceAll("\\s+", " "), Instant.now());
        user.updatePreferences(theme, timezone, locale, Instant.now());
        auditService.record(AuthEventType.PROFILE_UPDATED, userId);
    }

    @Transactional
    public void changePassword(UUID userId, String currentPassword, String newPassword) {
        UserAccount user = findUser(userId);
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCurrentPasswordException();
        }
        user.changePassword(passwordEncoder.encode(newPassword), Instant.now());
        sessionRegistry.getAllPrincipals().stream()
                .filter(principal -> principal instanceof com.taskos.auth.service.TaskOsUserDetails details
                        && details.getId().equals(userId))
                .flatMap(principal -> sessionRegistry.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
        auditService.record(AuthEventType.PASSWORD_CHANGED, userId);
    }

    @Transactional
    public void recordSuccessfulLogin(UUID userId) {
        UserAccount user = findUser(userId);
        user.recordSuccessfulLogin(Instant.now());
        auditService.record(AuthEventType.LOGIN_SUCCEEDED, userId);
    }

    private UserAccount findUser(UUID userId) {
        return users.findById(userId).orElseThrow(() -> new AccountNotFoundException());
    }

    public static final class AccountNotFoundException extends RuntimeException {
        public AccountNotFoundException() {
            super("Account was not found.");
        }
    }

    public static final class InvalidCurrentPasswordException extends RuntimeException {
        public InvalidCurrentPasswordException() {
            super("The current password was incorrect.");
        }
    }
}
