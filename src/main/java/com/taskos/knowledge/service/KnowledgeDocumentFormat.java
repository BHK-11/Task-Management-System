package com.taskos.knowledge.service;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public enum KnowledgeDocumentFormat {
    PDF("pdf", "application/pdf"),
    DOCX("docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document"),
    XLSX("xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"),
    PPTX("pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
    TXT("txt", "text/plain"),
    MD("md", "text/markdown");

    private final String extension;
    private final String contentType;

    KnowledgeDocumentFormat(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public static KnowledgeDocumentFormat detect(String fileName, byte[] bytes) {
        if (fileName == null || bytes == null || bytes.length == 0)
            throw new IllegalArgumentException("Choose a non-empty supported document.");
        String lower = fileName.toLowerCase(Locale.ROOT);
        KnowledgeDocumentFormat format = java.util.Arrays.stream(values())
                .filter(value -> lower.endsWith("." + value.extension)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Supported files: PDF, DOCX, XLSX, PPTX, TXT, and Markdown."));
        if (format == PDF && !new String(bytes, 0, Math.min(bytes.length, 5), StandardCharsets.US_ASCII).equals("%PDF-"))
            throw new IllegalArgumentException("The file contents do not match a PDF.");
        if (format == DOCX || format == XLSX || format == PPTX) {
            if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K')
                throw new IllegalArgumentException("The file contents do not match an Office document.");
        }
        if (format == TXT || format == MD) {
            try {
                String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                        .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
                if (text.indexOf('\0') >= 0) throw new IllegalArgumentException("Binary files cannot be uploaded as text.");
            } catch (CharacterCodingException ex) {
                throw new IllegalArgumentException("Text files must use UTF-8 encoding.");
            }
        }
        return format;
    }

    public String extension() { return extension; }
    public String contentType() { return contentType; }
}
