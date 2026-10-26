package com.taskos.knowledge.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.taskos.ai.service.AiPlannerClient;
import com.taskos.knowledge.entity.KnowledgeDocument;
import com.taskos.knowledge.entity.KnowledgeIndexState;
import com.taskos.knowledge.repository.KnowledgeChunkRepository;
import com.taskos.knowledge.repository.KnowledgeDocumentRepository;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class KnowledgeDocumentServiceTest {
    @TempDir Path temp;

    @Test
    void uploadStoresAndIndexesTextInAnOwnerDirectory() throws Exception {
        UUID owner = UUID.randomUUID();
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
        AiPlannerClient extractor = mock(AiPlannerClient.class);
        when(documents.findAllByOwnerIdAndFileNameIgnoreCaseOrderByVersionDesc(owner, "notes.txt")).thenReturn(List.of());
        when(documents.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(documents.save(any())).thenAnswer(call -> call.getArgument(0));
        when(extractor.extractDocument(eq("txt"), any())).thenReturn(new AiPlannerClient.DocumentExtraction("txt", 8,
                List.of(new AiPlannerClient.ExtractionSegment("Line 1", "Review 📌"))));

        KnowledgeDocumentService service = new KnowledgeDocumentService(documents, chunks, extractor,
                new KnowledgeDocumentStorageService(temp.toString()), Clock.fixed(Instant.parse("2026-10-27T00:00:00Z"), ZoneOffset.UTC));
        KnowledgeDocument document = service.upload(owner, "C:\\Users\\Aditya\\notes.txt", "Review 📌".getBytes(StandardCharsets.UTF_8));

        assertEquals("notes.txt", document.getFileName());
        assertEquals(KnowledgeIndexState.INDEXED, document.getIndexState());
        assertEquals(8, document.getExtractedCharacterCount());
        assertTrue(document.isCurrentVersion());
        assertNotNull(document.getStorageKey());
        assertTrue(java.nio.file.Files.exists(temp.resolve(owner.toString()).resolve(document.getStorageKey())));
        verify(chunks).saveAll(anyList());
    }

    @Test
    void failedNewVersionDoesNotDisplaceLastIndexedVersion() {
        UUID owner = UUID.randomUUID();
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        KnowledgeChunkRepository chunks = mock(KnowledgeChunkRepository.class);
        AiPlannerClient extractor = mock(AiPlannerClient.class);
        KnowledgeDocument previous = KnowledgeDocument.processing(owner, UUID.randomUUID(), 1, "notes.txt", "txt",
                "text/plain", UUID.randomUUID().toString(), 10, "a".repeat(64), Instant.parse("2026-10-26T00:00:00Z"));
        previous.indexed(4, Instant.parse("2026-10-26T00:01:00Z"));
        when(documents.findAllByOwnerIdAndFileNameIgnoreCaseOrderByVersionDesc(owner, "notes.txt")).thenReturn(List.of(previous));
        when(documents.saveAndFlush(any())).thenAnswer(call -> call.getArgument(0));
        when(documents.save(any())).thenAnswer(call -> call.getArgument(0));
        when(extractor.extractDocument(eq("txt"), any())).thenThrow(new com.taskos.ai.service.AiCopilotException("Extractor unavailable"));

        KnowledgeDocumentService service = new KnowledgeDocumentService(documents, chunks, extractor,
                new KnowledgeDocumentStorageService(temp.toString()), Clock.systemUTC());
        KnowledgeDocument failed = service.upload(owner, "notes.txt", "new notes".getBytes(StandardCharsets.UTF_8));

        assertEquals(KnowledgeIndexState.FAILED, failed.getIndexState());
        org.junit.jupiter.api.Assertions.assertFalse(failed.isCurrentVersion());
        assertTrue(previous.isCurrentVersion());
    }

    @Test
    void documentLookupUsesTheAuthenticatedOwnerId() {
        UUID documentId = UUID.randomUUID();
        UUID owner = UUID.randomUUID();
        KnowledgeDocumentRepository documents = mock(KnowledgeDocumentRepository.class);
        when(documents.findByIdAndOwnerId(documentId, owner)).thenReturn(java.util.Optional.empty());
        KnowledgeDocumentService service = new KnowledgeDocumentService(documents, mock(KnowledgeChunkRepository.class),
                mock(AiPlannerClient.class), new KnowledgeDocumentStorageService(temp.toString()), Clock.systemUTC());
        assertThrows(KnowledgeDocumentNotFoundException.class, () -> service.load(owner, documentId));
    }

    @Test
    void verifiedDownloadRejectsTamperedFileBytes() throws Exception {
        UUID owner = UUID.randomUUID();
        UUID key = UUID.randomUUID();
        KnowledgeDocumentStorageService storage = new KnowledgeDocumentStorageService(temp.toString());
        byte[] original = "verified".getBytes(StandardCharsets.UTF_8);
        storage.store(owner, key.toString(), original);
        Path stored = temp.resolve(owner.toString()).resolve(key.toString());
        java.nio.file.Files.writeString(stored, "changed", StandardCharsets.UTF_8);
        assertThrows(KnowledgeDocumentException.class,
                () -> storage.load(owner, key.toString(), original.length, KnowledgeDocumentStorageService.digest(original)));
    }
}
