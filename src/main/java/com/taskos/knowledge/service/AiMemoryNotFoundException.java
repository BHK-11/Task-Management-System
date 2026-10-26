package com.taskos.knowledge.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class AiMemoryNotFoundException extends RuntimeException {
    public AiMemoryNotFoundException() { super("Memory not found."); }
}
