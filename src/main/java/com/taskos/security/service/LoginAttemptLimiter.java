package com.taskos.security.service;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import com.taskos.common.EmailAddress;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

/** Process-local throttle for this single-node application foundation. */
@Component
public class LoginAttemptLimiter {
    public static final int MAX_FAILURES = 10;
    public static final Duration WINDOW = Duration.ofMinutes(15);
    private static final int MAX_TRACKED_KEYS = 20_000;
    private static final byte[] HMAC_KEY = new byte[32];

    static {
        new SecureRandom().nextBytes(HMAC_KEY);
    }

    private final ConcurrentHashMap<String, AttemptWindow> attempts = new ConcurrentHashMap<>();
    private final AtomicInteger eventsSinceCleanup = new AtomicInteger();

    public boolean isBlocked(String remoteAddress, String email) {
        String key = keyFor(remoteAddress, email);
        AttemptWindow window = attempts.get(key);
        if (window == null) return false;
        if (window.expiresAt().isBefore(Instant.now())) {
            attempts.remove(key, window);
            return false;
        }
        return window.failureCount() >= MAX_FAILURES;
    }

    public void recordFailure(String remoteAddress, String email) {
        String key = keyFor(remoteAddress, email);
        Instant now = Instant.now();
        attempts.compute(key, (ignored, current) -> {
            if (current == null || current.expiresAt().isBefore(now)) {
                return new AttemptWindow(1, now.plus(WINDOW), now);
            }
            return new AttemptWindow(current.failureCount() + 1, current.expiresAt(), now);
        });
        if (eventsSinceCleanup.incrementAndGet() % 128 == 0 || attempts.size() > MAX_TRACKED_KEYS) {
            removeExpiredAndBoundedEntries(Instant.now());
        }
    }

    public void recordSuccess(String remoteAddress, String email) {
        attempts.remove(keyFor(remoteAddress, email));
    }

    @Scheduled(fixedDelay = 60_000)
    public void cleanExpiredAttempts() {
        removeExpiredAndBoundedEntries(Instant.now());
    }

    private String keyFor(String remoteAddress, String email) {
        String input = (remoteAddress == null ? "unknown" : remoteAddress) + "\n"
                + EmailAddress.canonicalize(email);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(HMAC_KEY, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(input.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to prepare a login throttle key", exception);
        }
    }

    private void removeExpiredAndBoundedEntries(Instant now) {
        attempts.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
        if (attempts.size() > MAX_TRACKED_KEYS) {
            int overflow = attempts.size() - MAX_TRACKED_KEYS;
            attempts.entrySet().stream()
                    .sorted(Map.Entry.comparingByValue((left, right) -> left.lastFailureAt().compareTo(right.lastFailureAt())))
                    .limit(overflow)
                    .map(Map.Entry::getKey)
                    .forEach(attempts::remove);
        }
    }

    private record AttemptWindow(int failureCount, Instant expiresAt, Instant lastFailureAt) {
    }
}
