package com.taskos.ai.service;

import com.taskos.ai.dto.AiModelMode;
import com.taskos.ai.entity.AiModelPreference;
import com.taskos.ai.repository.AiModelPreferenceRepository;
import java.time.Clock;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiModelPreferenceService {
    private static final Set<String> PROVIDERS = Set.of("openai", "anthropic", "gemini", "xai", "ollama");
    private static final java.util.regex.Pattern MODEL_ID = java.util.regex.Pattern.compile("[A-Za-z0-9._:/+\\-]{1,128}");

    private final AiModelPreferenceRepository preferences;
    private final Clock clock;

    public AiModelPreferenceService(AiModelPreferenceRepository preferences, Clock clock) {
        this.preferences = preferences;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Selection get(UUID ownerId) {
        return preferences.findById(ownerId).map(AiModelPreferenceService::selection)
                .orElse(new Selection(AiModelMode.AUTO, null, null, true, false));
    }

    @Transactional
    public void save(UUID ownerId, AiModelMode mode, String providerId, String modelId,
            boolean allowCloudProcessing, boolean allowFallback) {
        if (mode == null) throw new IllegalArgumentException("Choose an AI mode.");
        if (mode == AiModelMode.MANUAL) {
            if (providerId == null || !PROVIDERS.contains(providerId)
                    || modelId == null || !MODEL_ID.matcher(modelId).matches())
                throw new IllegalArgumentException("Choose a listed provider and model for manual mode.");
            if (!allowCloudProcessing && !providerId.equals("ollama"))
                throw new IllegalArgumentException("Local-only privacy settings require an Ollama model.");
        } else {
            providerId = null;
            modelId = null;
        }
        var current = preferences.findById(ownerId).orElseGet(() -> AiModelPreference.defaults(ownerId, clock.instant()));
        current.update(mode, providerId, modelId, allowCloudProcessing, allowFallback, clock.instant());
        preferences.save(current);
    }

    private static Selection selection(AiModelPreference preference) {
        return new Selection(preference.getMode(), preference.getProviderId(), preference.getModelId(),
                preference.isAllowCloudProcessing(), preference.isAllowFallback());
    }

    public record Selection(AiModelMode mode, String providerId, String modelId,
            boolean allowCloudProcessing, boolean allowFallback) {}
}
