package com.taskos.knowledge.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.NOT_FOUND)
public class KnowledgeDocumentNotFoundException extends RuntimeException {
    public KnowledgeDocumentNotFoundException() { super("That document is not available."); }
}
