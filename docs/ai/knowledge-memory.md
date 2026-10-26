# TASK OS Phase 17: memory and knowledge foundation

## Implemented

- Persistent memory is disabled for new users until they enable it. Only memories explicitly entered and confirmed in **Memory settings** are stored; the AI cannot write directly to this store. The service caps active memories at 100 per account.
- Memory entries are personal and owner-scoped. The UI supports review, correction through a superseding version, deletion of an entry and its full version chain, and revocation of memory use.
- When enabled, up to four keyword-relevant memories (500 characters each) can be sent as a separate low-priority AI context field. The current instruction takes precedence, the Python gateway marks recalled text untrusted, and the original plan request remains unchanged. The user’s existing model and cloud-processing preferences still control routing.
- The Knowledge library accepts PDF, DOCX, XLSX, PPTX, TXT, and Markdown files up to 10 MB. The Java service checks format signatures and UTF-8 text, stores originals outside the public web root, and verifies size and SHA-256 on download. The private FastAPI service extracts bounded text with page, slide, sheet-row, paragraph, or line locators.
- Re-uploads with the same file name create retained versions. The library supports up to 100 total versions per account, retry after an indexing failure, owner-scoped exact-phrase search, source excerpts, source-location citations, download, and deletion.

## Current limits

- Search is exact phrase matching over extracted chunks. PostgreSQL full-text ranking, embeddings, pgvector, semantic/hybrid retrieval, and RAG answer generation are not implemented. The repository and test environment have not confirmed the PostgreSQL `vector` extension or a configured embeddings provider, so no vector capability is claimed.
- OCR for scanned PDFs/images is not implemented. DOCX citations identify paragraph/table rows rather than rendered page numbers.
- Knowledge files and memories are personal-only. Workspace/project-scoped access, conversation compaction, episodic job memory, AI-generated memory proposals, a context-pack builder, and retrieval audit records remain out of scope for this foundation slice.
- Extraction is synchronous through the configured private AI service. Failures are persisted and retryable, but there is no durable asynchronous indexing queue or worker timeout/isolation boundary yet. Do not expose the extraction service directly to untrusted networks; keep its service token private and restrict its network path.
- OpenViking remains optional and uninstalled. Its official repository identifies the main project as AGPLv3 and lists component-specific exceptions; legal review, tenant/ACL verification, and a TASK OS benchmark are still required before adoption.
- Hermes remains unintegrated. Its own security documentation recommends a container backend and resource limits and warns that file-write guards do not sandbox a hostile agent; TASK OS would need to keep authorization in Java and expose only scoped tools through an isolated runtime.

## Storage and configuration

- Original files default to `${user.home}/.taskos/knowledge`, separated by owner UUID. Set `TASKOS_KNOWLEDGE_DIRECTORY` to move the storage root.
- The Java service calls `TASKOS_AI_SERVICE_URL` with `TASKOS_AI_SERVICE_TOKEN` for extraction. The token must be at least 32 characters; the existing client only accepts HTTPS or localhost HTTP.
- Flyway V26 adds opt-in memory tables, V27 adds document versions and extracted chunks, and V28 adds optimistic locking to memory records. These migrations are H2-compatible; PostgreSQL-specific vector DDL is intentionally absent.
