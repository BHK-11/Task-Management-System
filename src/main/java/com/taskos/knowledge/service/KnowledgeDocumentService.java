package com.taskos.knowledge.service;

import com.taskos.ai.service.AiCopilotException;
import com.taskos.ai.service.AiPlannerClient;
import com.taskos.knowledge.entity.KnowledgeChunk;
import com.taskos.knowledge.entity.KnowledgeDocument;
import com.taskos.knowledge.entity.KnowledgeIndexState;
import com.taskos.knowledge.repository.KnowledgeChunkRepository;
import com.taskos.knowledge.repository.KnowledgeDocumentRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeDocumentService {
    private static final int MAX_BYTES = 10 * 1024 * 1024;
    private static final int MAX_EXTRACTED_CHARS = 1_000_000;
    private static final int MAX_SEGMENTS = 2_000;
    private static final int CHUNK_CODE_POINTS = 1_500;
    private static final int MAX_DOCUMENT_VERSIONS = 100;

    private final KnowledgeDocumentRepository documents;
    private final KnowledgeChunkRepository chunks;
    private final AiPlannerClient extractor;
    private final KnowledgeDocumentStorageService storage;
    private final Clock clock;

    public KnowledgeDocumentService(KnowledgeDocumentRepository documents, KnowledgeChunkRepository chunks,
            AiPlannerClient extractor, KnowledgeDocumentStorageService storage, Clock clock) {
        this.documents = documents;
        this.chunks = chunks;
        this.extractor = extractor;
        this.storage = storage;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<KnowledgeDocument> library(UUID ownerId) {
        return documents.findAllByOwnerIdOrderByCreatedAtDesc(ownerId, PageRequest.of(0, 100));
    }

    @Transactional
    public KnowledgeDocument upload(UUID ownerId, String rawFileName, byte[] bytes) {
        if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
            throw new KnowledgeDocumentException("Choose a document up to 10 MB.");
        if (documents.countByOwnerId(ownerId) >= MAX_DOCUMENT_VERSIONS)
            throw new KnowledgeDocumentException("The knowledge library is limited to 100 document versions. Delete an old version before uploading another.");
        String fileName = safeFileName(rawFileName);
        KnowledgeDocumentFormat format = KnowledgeDocumentFormat.detect(fileName, bytes);
        List<KnowledgeDocument> versions = documents.findAllByOwnerIdAndFileNameIgnoreCaseOrderByVersionDesc(ownerId, fileName);
        KnowledgeDocument latest = versions.isEmpty() ? null : versions.getFirst();
        UUID logicalId = latest == null ? UUID.randomUUID() : latest.getLogicalId();
        int version = latest == null ? 1 : latest.getVersion() + 1;
        Instant now = clock.instant();
        String key = UUID.randomUUID().toString();
        KnowledgeDocument document = KnowledgeDocument.processing(ownerId, logicalId, version, fileName,
                format.extension(), format.contentType(), key, bytes.length,
                KnowledgeDocumentStorageService.digest(bytes), now);
        storage.store(ownerId, key, bytes);
        documents.saveAndFlush(document);
        return index(document, versions, bytes);
    }

    @Transactional
    public KnowledgeDocument retry(UUID ownerId, UUID documentId) {
        KnowledgeDocument document = owned(ownerId, documentId);
        if (document.getIndexState() != KnowledgeIndexState.FAILED)
            throw new KnowledgeDocumentException("Only a failed document can be retried.");
        byte[] bytes = storage.load(ownerId, document.getStorageKey(), document.getSize(), document.getSha256()).bytes();
        List<KnowledgeDocument> versions = documents.findAllByOwnerIdAndLogicalIdOrderByVersionDesc(ownerId, document.getLogicalId());
        return index(document, versions, bytes);
    }

    @Transactional(readOnly = true)
    public List<SearchResult> search(UUID ownerId, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.isEmpty()) return List.of();
        if (query.length() > 120) throw new KnowledgeDocumentException("Keep the search under 120 characters.");
        return chunks.searchOwned(ownerId, KnowledgeIndexState.INDEXED, query, PageRequest.of(0, 30)).stream()
                .map(chunk -> new SearchResult(chunk.getDocument().getId(), chunk.getDocument().getFileName(),
                        chunk.getDocument().getVersion(), chunk.getLocator(), excerpt(chunk.getContent(), query)))
                .toList();
    }

    @Transactional(readOnly = true)
    public Download load(UUID ownerId, UUID documentId) {
        KnowledgeDocument document = owned(ownerId, documentId);
        return new Download(document.getFileName(), document.getContentType(),
                storage.load(ownerId, document.getStorageKey(), document.getSize(), document.getSha256()).bytes());
    }

    @Transactional
    public void delete(UUID ownerId, UUID documentId) {
        KnowledgeDocument document = owned(ownerId, documentId);
        List<KnowledgeDocument> versions = documents.findAllByOwnerIdAndLogicalIdOrderByVersionDesc(ownerId, document.getLogicalId());
        if (document.isCurrentVersion()) {
            versions.stream().filter(version -> !version.getId().equals(documentId)
                            && version.getIndexState() == KnowledgeIndexState.INDEXED)
                    .max(Comparator.comparingInt(KnowledgeDocument::getVersion)).ifPresent(version -> {
                        version.indexed(version.getExtractedCharacterCount(), clock.instant());
                        documents.save(version);
                    });
        }
        chunks.deleteAllByDocument_Id(documentId);
        documents.delete(document);
        storage.deleteAfterCommit(ownerId, document.getStorageKey());
    }

    private KnowledgeDocument index(KnowledgeDocument document, List<KnowledgeDocument> versions, byte[] bytes) {
        try {
            var extraction = extractor.extractDocument(document.getFormat(), bytes);
            long count = extraction.segments().stream().mapToLong(segment -> segment == null || segment.text() == null ? 0
                    : segment.text().codePointCount(0, segment.text().length())).sum();
            if (count < 1 || count > MAX_EXTRACTED_CHARS || extraction.characterCount() != count)
                throw new KnowledgeDocumentException("The extracted document text failed validation.");
            List<KnowledgeChunk> indexed = toChunks(document, extraction.segments());
            chunks.deleteAllByDocument_Id(document.getId());
            chunks.saveAll(indexed);
            versions.stream().filter(version -> !version.getId().equals(document.getId()) && version.isCurrentVersion())
                    .forEach(version -> { version.notCurrent(); documents.save(version); });
            document.indexed((int) count, clock.instant());
        } catch (AiCopilotException | KnowledgeDocumentException ex) {
            chunks.deleteAllByDocument_Id(document.getId());
            document.failed(message(ex.getMessage()));
        }
        return documents.save(document);
    }

    private List<KnowledgeChunk> toChunks(KnowledgeDocument document, List<AiPlannerClient.ExtractionSegment> segments) {
        if (segments == null || segments.isEmpty() || segments.size() > MAX_SEGMENTS)
            throw new KnowledgeDocumentException("The extracted document did not contain valid searchable text.");
        List<KnowledgeChunk> result = new ArrayList<>();
        int order = 0;
        for (var segment : segments) {
            if (segment == null || segment.text() == null || segment.locator() == null)
                throw new KnowledgeDocumentException("The extracted document did not contain valid searchable text.");
            String text = segment.text().strip();
            String locator = segment.locator().strip();
            if (text.isEmpty() || locator.isEmpty() || locator.length() > 240)
                throw new KnowledgeDocumentException("The extracted document did not contain valid searchable text.");
            int offset = 0;
            while (offset < text.length()) {
                int end = text.offsetByCodePoints(offset, Math.min(CHUNK_CODE_POINTS, text.codePointCount(offset, text.length())));
                result.add(KnowledgeChunk.create(document.getOwnerId(), document, order++, locator,
                        text.substring(offset, end), clock.instant()));
                offset = end;
            }
        }
        return result;
    }

    private KnowledgeDocument owned(UUID ownerId, UUID id) {
        return documents.findByIdAndOwnerId(id, ownerId).orElseThrow(KnowledgeDocumentNotFoundException::new);
    }

    static String safeFileName(String raw) {
        if (raw == null) throw new KnowledgeDocumentException("Choose a document file.");
        String path = raw.replace('\\', '/');
        String name = path.substring(path.lastIndexOf('/') + 1).strip();
        if (name.isEmpty() || name.length() > 128 || name.chars().anyMatch(Character::isISOControl))
            throw new KnowledgeDocumentException("The file name is invalid or too long.");
        return name;
    }

    private static String message(String value) {
        String clean = value == null || value.isBlank() ? "Document extraction failed. Retry after checking the file." : value.strip();
        return clean.length() > 240 ? clean.substring(0, 240) : clean;
    }

    private static String excerpt(String text, String query) {
        int found = text.toLowerCase(Locale.ROOT).indexOf(query.toLowerCase(Locale.ROOT));
        int start = Math.max(0, found - 100);
        int end = Math.min(text.length(), Math.max(found + query.length(), start) + 180);
        return (start > 0 ? "…" : "") + text.substring(start, end) + (end < text.length() ? "…" : "");
    }

    public record SearchResult(UUID documentId, String fileName, int version, String locator, String excerpt) {}
    public record Download(String fileName, String contentType, byte[] bytes) {}
}
