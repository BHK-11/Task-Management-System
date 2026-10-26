package com.taskos.ai.service;

public class AiArtifactNotFoundException extends RuntimeException {
    public AiArtifactNotFoundException() { super("Deliverable not found."); }
}
