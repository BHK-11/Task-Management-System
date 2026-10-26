package com.taskos.auth.service;

import java.time.Instant;
import java.util.UUID;

import com.taskos.auth.entity.AuthEvent;
import com.taskos.auth.entity.AuthEventType;
import com.taskos.auth.repository.AuthEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthAuditService {
    private final AuthEventRepository events;

    public AuthAuditService(AuthEventRepository events) {
        this.events = events;
    }

    @Transactional
    public void record(AuthEventType type, UUID userId) {
        events.save(new AuthEvent(userId, type, Instant.now()));
    }

    @Transactional
    public int purgeOlderThan(Instant cutoff) {
        return events.deleteOlderThan(cutoff);
    }
}
