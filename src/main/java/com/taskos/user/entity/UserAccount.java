package com.taskos.user.entity;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

@Entity
@Table(name = "users", schema = "task_os", indexes = {
        @Index(name = "ix_users_account_status", columnList = "account_status")
})
public class UserAccount {
    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 512)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 16)
    private AccountStatus accountStatus = AccountStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "theme_preference", nullable = false, length = 16)
    private ThemePreference themePreference = ThemePreference.SYSTEM;

    @Column(nullable = false, length = 64)
    private String timezone = "UTC";

    @Column(nullable = false, length = 10)
    private String locale = "en";

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_roles", schema = "task_os",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_name"))
    private Set<Role> roles = new LinkedHashSet<>();

    protected UserAccount() {
    }

    public static UserAccount register(String displayName, String email, String passwordHash, Role userRole,
            Instant now) {
        UserAccount account = new UserAccount();
        account.id = UUID.randomUUID();
        account.displayName = displayName;
        account.email = email;
        account.passwordHash = passwordHash;
        account.accountStatus = AccountStatus.ACTIVE;
        account.themePreference = ThemePreference.SYSTEM;
        account.timezone = "UTC";
        account.locale = "en";
        account.createdAt = now;
        account.updatedAt = now;
        account.roles.add(userRole);
        return account;
    }

    public void updateDisplayName(String displayName, Instant now) {
        this.displayName = displayName;
        this.updatedAt = now;
    }

    public void updatePreferences(ThemePreference themePreference, String timezone, String locale, Instant now) {
        this.themePreference = themePreference;
        this.timezone = timezone;
        this.locale = locale;
        this.updatedAt = now;
    }

    public void changePassword(String passwordHash, Instant now) {
        this.passwordHash = passwordHash;
        this.updatedAt = now;
    }

    public void recordSuccessfulLogin(Instant now) {
        this.lastLoginAt = now;
    }

    @PreUpdate
    private void touchUpdatedAt() {
        if (updatedAt == null) updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public AccountStatus getAccountStatus() { return accountStatus; }
    public ThemePreference getThemePreference() { return themePreference; }
    public String getTimezone() { return timezone; }
    public String getLocale() { return locale; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getLastLoginAt() { return lastLoginAt; }
    public Set<Role> getRoles() { return Set.copyOf(roles); }
}
