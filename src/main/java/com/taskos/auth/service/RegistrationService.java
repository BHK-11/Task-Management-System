package com.taskos.auth.service;

import java.time.Instant;
import java.util.Locale;

import com.taskos.auth.entity.AuthEventType;
import com.taskos.auth.service.AuthAuditService;
import com.taskos.common.EmailAddress;
import com.taskos.user.entity.Role;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.RoleRepository;
import com.taskos.user.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrationService {
    private final UserAccountRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder passwordEncoder;
    private final AuthAuditService auditService;

    public RegistrationService(UserAccountRepository users, RoleRepository roles,
            PasswordEncoder passwordEncoder, AuthAuditService auditService) {
        this.users = users;
        this.roles = roles;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
    }

    @Transactional
    public UserAccount register(String displayName, String email, String rawPassword) {
        String canonicalEmail = EmailAddress.canonicalize(email);
        if (users.existsByEmail(canonicalEmail)) throw new DuplicateEmailException();

        Role standardUserRole = roles.findByName("ROLE_USER")
                .orElseThrow(() -> new IllegalStateException("Required application role is missing"));
        String normalizedName = displayName.strip().replaceAll("\\s+", " ");
        UserAccount account = UserAccount.register(normalizedName, canonicalEmail,
                passwordEncoder.encode(rawPassword), standardUserRole, Instant.now());
        try {
            UserAccount saved = users.saveAndFlush(account);
            auditService.record(AuthEventType.ACCOUNT_REGISTERED, saved.getId());
            return saved;
        } catch (DataIntegrityViolationException ex) {
            // The unique constraint is authoritative when concurrent requests race.
            throw new DuplicateEmailException();
        }
    }

    public static final class DuplicateEmailException extends RuntimeException {
        public DuplicateEmailException() {
            super("An account with this email address already exists.");
        }
    }
}
