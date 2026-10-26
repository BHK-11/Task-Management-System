package com.taskos.auth.repository;

import java.time.Instant;
import java.util.UUID;

import com.taskos.auth.entity.AuthEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface AuthEventRepository extends JpaRepository<AuthEvent, UUID> {
    @Modifying
    @Query("delete from AuthEvent event where event.occurredAt < :cutoff")
    int deleteOlderThan(Instant cutoff);
}
