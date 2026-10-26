package com.taskos.security.service;

import java.util.Locale;

import com.taskos.auth.service.TaskOsUserDetails;
import com.taskos.user.entity.AccountStatus;
import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DatabaseUserDetailsService implements UserDetailsService {
    private final UserAccountRepository users;

    public DatabaseUserDetailsService(UserAccountRepository users) {
        this.users = users;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        String canonicalEmail = email == null ? "" : email.strip().toLowerCase(Locale.ROOT);
        UserAccount account = users.findByEmail(canonicalEmail)
                .orElseThrow(() -> new UsernameNotFoundException("Account not found"));
        return new TaskOsUserDetails(account.getId(), account.getDisplayName(), account.getEmail(),
                account.getPasswordHash(), account.getAccountStatus() == AccountStatus.ACTIVE,
                account.getRoles().stream().map(role -> new SimpleGrantedAuthority(role.getName())).toList());
    }
}
