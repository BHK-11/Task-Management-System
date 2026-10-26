package com.taskos.browser.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;

class BrowserWorkerClientTest {
    @Test
    void acceptsOnlySimpleHttpsPageUrls() {
        assertEquals(URI.create("https://example.org/guide"), BrowserWorkerClient.target(" https://example.org/guide "));
        for (String value : new String[] {"http://example.org", "file:///etc/passwd", "https://user:pass@example.org",
                "https://example.org/?token=secret", "https://example.org/#secret", "https://example.org:8443"})
            assertThrows(BrowserWorkerException.class, () -> BrowserWorkerClient.target(value));
    }
}
