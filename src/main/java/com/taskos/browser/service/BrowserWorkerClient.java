package com.taskos.browser.service;

import com.taskos.browser.dto.BrowserObservation;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class BrowserWorkerClient {
    private final String serviceUrl;
    private final String serviceToken;
    private final RestClient client;

    public BrowserWorkerClient(@Value("${taskos.browser.service-url:}") String serviceUrl,
            @Value("${taskos.browser.service-token:}") String serviceToken) {
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.strip().replaceAll("/+$", "");
        this.serviceToken = serviceToken == null ? "" : serviceToken;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(20));
        this.client = RestClient.builder().requestFactory(timeouts).build();
    }

    public boolean isConfigured() {
        if (serviceToken.length() < 32 || serviceUrl.isBlank()) return false;
        try {
            URI uri = URI.create(serviceUrl);
            String host = uri.getHost();
            if (!uri.isAbsolute() || host == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null) return false;
            return "https".equalsIgnoreCase(uri.getScheme())
                    || ("http".equalsIgnoreCase(uri.getScheme())
                    && List.of("localhost", "127.0.0.1", "::1").contains(host.toLowerCase(Locale.ROOT)));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public BrowserObservation observe(String rawUrl) {
        if (!isConfigured()) throw new BrowserWorkerException("The private browser worker is not configured.", "BROWSER_PROVIDER_UNAVAILABLE");
        URI target = target(rawUrl);
        try {
            BrowserObservation observation = client.post().uri(serviceUrl + "/v1/observe")
                    .header("X-TaskOS-Service-Token", serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("url", target.toString()))
                    .retrieve().body(BrowserObservation.class);
            if (observation == null || observation.origin() == null || observation.title() == null
                    || observation.visibleText() == null || observation.title().length() > 240
                    || observation.visibleText().length() > 12_000 || observation.blockedRequestCount() < 0)
                throw new BrowserWorkerException("The browser worker returned an invalid observation.", "INVALID_BROWSER_RESPONSE");
            return observation;
        } catch (HttpStatusCodeException ex) {
            int status = ex.getStatusCode().value();
            if (status == 403) throw new BrowserWorkerException("That website is not allowed by the browser worker's origin or network policy.", "ORIGIN_BLOCKED", ex);
            if (status == 429) throw new BrowserWorkerException("The browser worker is busy. Try again shortly.", "BROWSER_BUSY", ex);
            if (status == 503) throw new BrowserWorkerException("The browser worker has no approved public origins or is unavailable.", "BROWSER_PROVIDER_UNAVAILABLE", ex);
            throw new BrowserWorkerException("The browser worker could not safely observe the page.", "BROWSER_OBSERVATION_FAILED", ex);
        } catch (RestClientException ex) {
            throw new BrowserWorkerException("The browser worker rejected the request or could not safely observe the approved page.",
                    "BROWSER_OBSERVATION_FAILED", ex);
        }
    }

    public static URI target(String rawUrl) {
        try {
            URI uri = URI.create(rawUrl == null ? "" : rawUrl.strip()).normalize();
            if (!uri.isAbsolute() || !"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getUserInfo() != null || uri.getFragment() != null || uri.getRawQuery() != null
                    || (uri.getPort() != -1 && uri.getPort() != 443))
                throw new IllegalArgumentException();
            return uri;
        } catch (IllegalArgumentException ex) {
            throw new BrowserWorkerException("Enter an HTTPS URL without credentials, query parameters, or a fragment.",
                    "UNAUTHORIZED_TARGET", ex);
        }
    }
}
