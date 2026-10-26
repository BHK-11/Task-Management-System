package com.taskos.ai.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AiPlannerClientTest {
    @Test
    void unconfiguredProviderDoesNotExposeCredentials() {
        String token = "short-test-token";
        var client = new AiPlannerClient("https://provider.example", token);

        var failure = assertThrows(AiCopilotException.class, () -> client.plan(null));

        assertFalse(failure.getMessage().contains(token));
    }

    @Test
    void rejectsCredentialBearingOrRemotePlainHttpPlannerUrls() {
        String token = "test-service-token-that-is-never-a-real-credential";

        assertFalse(new AiPlannerClient("https://user:pass@example.test", token).isConfigured());
        assertFalse(new AiPlannerClient("https://example.test/?token=secret", token).isConfigured());
        assertFalse(new AiPlannerClient("http://example.test", token).isConfigured());
    }
}
