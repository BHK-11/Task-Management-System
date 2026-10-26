package com.taskos.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class KnowledgeDocumentFormatTest {
    @Test
    void extensionAndContentMustMatchAndTextMustBeUtf8() {
        assertEquals(KnowledgeDocumentFormat.PDF, KnowledgeDocumentFormat.detect("guide.pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII)));
        assertEquals(KnowledgeDocumentFormat.MD, KnowledgeDocumentFormat.detect("notes.md", "# Notes".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeDocumentFormat.detect("guide.pdf", "not a PDF".getBytes(StandardCharsets.UTF_8)));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeDocumentFormat.detect("notes.txt", new byte[] {(byte) 0xff}));
        assertThrows(IllegalArgumentException.class, () -> KnowledgeDocumentFormat.detect("notes.exe", new byte[] {1}));
    }
}
