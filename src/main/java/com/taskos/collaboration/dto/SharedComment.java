package com.taskos.collaboration.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record SharedComment(UUID id, UUID authorId, String authorName, String body, Instant createdAt,
        Instant editedAt, List<String> mentions, List<UUID> mentionIds, boolean editable) { }
