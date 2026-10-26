package com.taskos.ai.service;

import com.taskos.ai.dto.AiPlannerRequest;
import com.taskos.ai.dto.AiPlannerResponse;
import com.taskos.ai.dto.AiModelCatalog;
import java.util.List;
import com.taskos.ai.dto.AiWorkPlannerResponse;
import java.net.URI;
import java.io.IOException;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiPlannerClient {
    private final String serviceUrl;
    private final String serviceToken;
    private final RestClient client;

    public AiPlannerClient(@Value("${taskos.ai.service-url:}") String serviceUrl,
            @Value("${taskos.ai.service-token:}") String serviceToken) {
        this.serviceUrl = serviceUrl == null ? "" : serviceUrl.strip().replaceAll("/+$", "");
        this.serviceToken = serviceToken == null ? "" : serviceToken;
        SimpleClientHttpRequestFactory timeouts = new SimpleClientHttpRequestFactory();
        timeouts.setConnectTimeout(Duration.ofSeconds(2));
        timeouts.setReadTimeout(Duration.ofSeconds(50));
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
                    || ("http".equalsIgnoreCase(uri.getScheme()) && host != null
                    && (host.equalsIgnoreCase("localhost") || host.equals("127.0.0.1") || host.equals("::1")));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    public AiPlannerResponse plan(AiPlannerRequest request) {
        if (!isConfigured()) {
            throw new AiCopilotException("The private AI planner is not configured yet.");
        }
        try {
            AiPlannerResponse response = client.post()
                    .uri(serviceUrl + "/v1/plan")
                    .header("X-TaskOS-Service-Token", serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(AiPlannerResponse.class);
            if (response == null || response.plan() == null) {
                throw new AiCopilotException("The AI planner returned no plan. No TASK OS data was changed.");
            }
            return response;
        } catch (RestClientException ex) {
            throw new AiCopilotException("The AI planner is unavailable or not configured. No TASK OS data was changed.", ex);
        }
    }

    public AiWorkPlannerResponse planWork(AiPlannerRequest request) {
        if (!isConfigured()) throw new AiCopilotException("The private AI planner is not configured yet.");
        try {
            AiWorkPlannerResponse response = client.post().uri(serviceUrl + "/v1/work-plan")
                    .header("X-TaskOS-Service-Token", serviceToken)
                    .contentType(MediaType.APPLICATION_JSON).body(request).retrieve()
                    .body(AiWorkPlannerResponse.class);
            if (response == null || response.plan() == null)
                throw new AiCopilotException("The AI planner returned no work plan. No TASK OS data was changed.");
            return response;
        } catch (RestClientException ex) {
            throw new AiCopilotException("The AI planner is unavailable or could not create a work plan. No TASK OS data was changed.", ex);
        }
    }

    public AiModelCatalog catalog() {
        if (!isConfigured()) return new AiModelCatalog(java.util.List.of(), "The private AI service is not configured.");
        try {
            AiModelCatalog catalog = client.get().uri(serviceUrl + "/v1/models")
                    .header("X-TaskOS-Service-Token", serviceToken).retrieve().body(AiModelCatalog.class);
            return catalog == null ? new AiModelCatalog(java.util.List.of(), "The model catalog is unavailable.") : catalog;
        } catch (RestClientException ex) {
            return new AiModelCatalog(java.util.List.of(), "The model catalog is unavailable.");
        }
    }

    public byte[] generateArtifact(String kind, String title, String content) {
        if (!isConfigured()) throw new AiCopilotException("The private AI planner is not configured yet.");
        try {
            return client.post().uri(serviceUrl + "/v1/artifacts/" + kind)
                    .header("X-TaskOS-Service-Token", serviceToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(java.util.Map.of("title", title, "content", content))
                    .exchange((request, response) -> {
                        String expectedType = switch (kind) {
                            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
                            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
                            case "pdf" -> "application/pdf";
                            case "txt" -> "text/plain";
                            default -> throw new AiCopilotException("The requested deliverable format is not supported.");
                        };
                        MediaType receivedType = response.getHeaders().getContentType();
                        if (!response.getStatusCode().is2xxSuccessful()
                                || !kind.equalsIgnoreCase(response.getHeaders().getFirst("X-TaskOS-Artifact-Format"))
                                || receivedType == null || !receivedType.isCompatibleWith(MediaType.parseMediaType(expectedType)))
                            throw new AiCopilotException("The document generator did not verify the requested format.");
                        try {
                            byte[] bytes = response.getBody().readNBytes(10 * 1024 * 1024 + 1);
                            if (bytes.length == 0 || bytes.length > 10 * 1024 * 1024)
                                throw new AiCopilotException("The generated file was empty or exceeded the 10 MB limit.");
                            return bytes;
                        } catch (IOException ex) {
                            throw new AiCopilotException("TASK OS could not read the generated file.", ex);
                        }
                    });
        } catch (RestClientException ex) {
            throw new AiCopilotException("The document generator is unavailable. No deliverable was saved.", ex);
        }
    }

    public DocumentExtraction extractDocument(String format, byte[] bytes) {
        if (!isConfigured()) throw new AiCopilotException("The private document extraction service is not configured yet.");
        if (!List.of("pdf", "docx", "xlsx", "pptx", "txt", "md").contains(format) || bytes == null || bytes.length == 0)
            throw new AiCopilotException("The requested document format is not supported.");
        try {
            DocumentExtraction extraction = client.post().uri(serviceUrl + "/v1/documents/extract/" + format)
                    .header("X-TaskOS-Service-Token", serviceToken)
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(bytes)
                    .retrieve().body(DocumentExtraction.class);
            if (extraction == null || !format.equals(extraction.format()) || extraction.segments() == null
                    || extraction.segments().isEmpty() || extraction.characterCount() < 1)
                throw new AiCopilotException("The document extractor returned no verified text.");
            return extraction;
        } catch (RestClientException ex) {
            throw new AiCopilotException("The private document extractor could not read this file.", ex);
        }
    }

    public record DocumentExtraction(String format, int characterCount, List<ExtractionSegment> segments) {}
    public record ExtractionSegment(String locator, String text) {}
}
