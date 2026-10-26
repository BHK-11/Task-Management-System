package com.taskos.knowledge.service;

public class AiMemoryConsentRequiredException extends RuntimeException {
    public AiMemoryConsentRequiredException() { super("Enable persistent memory before saving confirmed details."); }
}
