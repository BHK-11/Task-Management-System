package com.taskos.ai.dto;

import java.util.List;

public record AiModelCatalog(List<Provider> providers, String capabilityNote) {
    public record Provider(String id, String status, List<Model> models) {}
    public record Model(String id, List<String> capabilities, String lastVerified) {}
}
