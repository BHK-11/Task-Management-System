package com.taskos.knowledge.service;

import com.taskos.knowledge.entity.AiMemory;
import com.taskos.knowledge.entity.AiMemoryCategory;
import com.taskos.knowledge.entity.AiMemoryPreference;
import com.taskos.knowledge.entity.AiMemoryStatus;
import com.taskos.knowledge.repository.AiMemoryPreferenceRepository;
import com.taskos.knowledge.repository.AiMemoryRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiMemoryService {
    private static final int MAX_ACTIVE_MEMORIES = 100;
    private static final Pattern SECRET = Pattern.compile("(?i)(?:sk-[A-Za-z0-9_-]{16,}|gh[pousr]_[A-Za-z0-9_]{20,}|AKIA[0-9A-Z]{16}|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----|password\\s*[:=]|(?:api[_ -]?key|token|secret)\\s*[:=]\\s*\\S{8,}|bearer\\s+[A-Za-z0-9._~+/=-]{16,})");

    private final AiMemoryRepository memories;
    private final AiMemoryPreferenceRepository preferences;
    private final Clock clock;

    public AiMemoryService(AiMemoryRepository memories, AiMemoryPreferenceRepository preferences, Clock clock) {
        this.memories = memories;
        this.preferences = preferences;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Preference preference(UUID ownerId) {
        return preferences.findById(ownerId).map(value -> new Preference(value.isEnabled(), value.getConsentedAt(), value.getRevokedAt()))
                .orElse(new Preference(false, null, null));
    }

    @Transactional
    public void setEnabled(UUID ownerId, boolean enabled) {
        Instant now = clock.instant();
        AiMemoryPreference preference = preferences.findById(ownerId)
                .orElseGet(() -> AiMemoryPreference.defaults(ownerId, now));
        preference.setEnabled(enabled, now);
        preferences.save(preference);
    }

    @Transactional(readOnly = true)
    public List<AiMemory> list(UUID ownerId) {
        return memories.findAllByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, AiMemoryStatus.ACTIVE);
    }

    @Transactional
    public AiMemory add(UUID ownerId, AiMemoryCategory category, String content) {
        requireEnabled(ownerId);
        if (memories.countByOwnerIdAndStatus(ownerId, AiMemoryStatus.ACTIVE) >= MAX_ACTIVE_MEMORIES)
            throw new IllegalArgumentException("You have reached the 100 active-memory limit. Delete an item before adding another.");
        return memories.save(AiMemory.confirmed(ownerId, category, validate(content), "Added by you", null, clock.instant()));
    }

    @Transactional
    public AiMemory correct(UUID ownerId, UUID id, AiMemoryCategory category, String content) {
        requireEnabled(ownerId);
        AiMemory existing = memories.findByIdAndOwnerId(id, ownerId).filter(value -> value.getStatus() == AiMemoryStatus.ACTIVE)
                .orElseThrow(AiMemoryNotFoundException::new);
        Instant now = clock.instant();
        existing.supersede(now);
        memories.save(existing);
        return memories.save(AiMemory.confirmed(ownerId, category, validate(content), "Corrected by you", existing.getId(), now));
    }

    @Transactional
    public void delete(UUID ownerId, UUID id) {
        AiMemory selected = memories.findByIdAndOwnerId(id, ownerId).orElseThrow(AiMemoryNotFoundException::new);
        List<AiMemory> owned = memories.findAllByOwnerIdOrderByCreatedAtDesc(ownerId);
        Set<UUID> deleteIds = new HashSet<>();
        deleteIds.add(selected.getId());
        boolean added;
        do {
            added = false;
            for (AiMemory memory : owned) {
                if (memory.getSupersedesId() != null && deleteIds.contains(memory.getSupersedesId()))
                    added |= deleteIds.add(memory.getId());
                if (deleteIds.contains(memory.getId()) && memory.getSupersedesId() != null)
                    added |= deleteIds.add(memory.getSupersedesId());
            }
        } while (added);
        memories.deleteAll(owned.stream().filter(memory -> deleteIds.contains(memory.getId())).toList());
    }

    @Transactional(readOnly = true)
    public List<AiMemory> retrieve(UUID ownerId, String query, int limit) {
        if (!preference(ownerId).enabled() || query == null || query.isBlank()) return List.of();
        Set<String> terms = terms(query);
        if (terms.isEmpty()) return List.of();
        return memories.findAllByOwnerIdAndStatusOrderByCreatedAtDesc(ownerId, AiMemoryStatus.ACTIVE).stream()
                .filter(memory -> memory.getExpiresAt() == null || memory.getExpiresAt().isAfter(clock.instant()))
                .map(memory -> new Scored(memory, score(memory.getContent(), terms)))
                .filter(scored -> scored.score() > 0)
                .sorted(Comparator.comparingInt(Scored::score).reversed().thenComparing(value -> value.memory().getCreatedAt(), Comparator.reverseOrder()))
                .limit(Math.max(1, Math.min(limit, 10))).map(Scored::memory).toList();
    }

    private void requireEnabled(UUID ownerId) {
        if (!preference(ownerId).enabled()) throw new AiMemoryConsentRequiredException();
    }

    private String validate(String content) {
        if (content == null || content.isBlank() || content.length() > 2000)
            throw new IllegalArgumentException("Enter a memory of 1–2,000 characters.");
        String normalized = content.strip();
        if (SECRET.matcher(normalized).find())
            throw new IllegalArgumentException("This looks like a password or secret. Store preferences and work context only.");
        return normalized;
    }

    private static Set<String> terms(String query) {
        Set<String> terms = new HashSet<>();
        for (String term : query.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
            if (term.length() > 1) terms.add(term);
        return terms;
    }

    private static int score(String content, Set<String> terms) {
        Set<String> contentTerms = terms(content);
        contentTerms.retainAll(terms);
        return contentTerms.size();
    }

    private record Scored(AiMemory memory, int score) {}
    public record Preference(boolean enabled, Instant consentedAt, Instant revokedAt) {}
}
