package com.taskos.ai.service;

public class AiCopilotException extends RuntimeException {
    public AiCopilotException(String message) { super(message); }
    public AiCopilotException(String message, Throwable cause) { super(message, cause); }
}
