package com.taskos.time.service;

import com.taskos.user.entity.UserAccount;
import com.taskos.user.repository.UserAccountRepository;
import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class TimeZoneResolver {
    private static final ZoneId FALLBACK = ZoneId.of("UTC");
    private final UserAccountRepository users;
    public TimeZoneResolver(UserAccountRepository users) { this.users = users; }
    public ZoneId zone(UUID ownerId) {
        UserAccount user = users.findById(ownerId).orElseThrow(() -> new TimeRuleException("Your account is unavailable."));
        try { return ZoneId.of(user.getTimezone()); }
        catch (DateTimeException ex) { return FALLBACK; }
    }
}
