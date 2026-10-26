CREATE TABLE task_os.knowledge_documents (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    logical_id UUID NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    file_name VARCHAR(128) NOT NULL,
    format VARCHAR(8) NOT NULL CHECK (format IN ('pdf', 'docx', 'xlsx', 'pptx', 'txt', 'md')),
    content_type VARCHAR(128) NOT NULL,
    storage_key VARCHAR(80) NOT NULL UNIQUE,
    size BIGINT NOT NULL CHECK (size > 0 AND size <= 10485760),
    sha256 VARCHAR(64) NOT NULL,
    index_state VARCHAR(16) NOT NULL CHECK (index_state IN ('PROCESSING', 'INDEXED', 'FAILED')),
    current_version BOOLEAN NOT NULL DEFAULT FALSE,
    extracted_character_count INTEGER NOT NULL DEFAULT 0 CHECK (extracted_character_count >= 0),
    failure_message VARCHAR(240),
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    indexed_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT uq_knowledge_document_version UNIQUE(owner_id, logical_id, version)
);

CREATE INDEX ix_knowledge_documents_owner_current ON task_os.knowledge_documents(owner_id, current_version, created_at DESC);
CREATE INDEX ix_knowledge_documents_owner_name ON task_os.knowledge_documents(owner_id, file_name, version DESC);

CREATE TABLE task_os.knowledge_chunks (
    id UUID PRIMARY KEY,
    owner_id UUID NOT NULL REFERENCES task_os.users(id) ON DELETE CASCADE,
    document_id UUID NOT NULL REFERENCES task_os.knowledge_documents(id) ON DELETE CASCADE,
    chunk_order INTEGER NOT NULL CHECK (chunk_order >= 0),
    locator VARCHAR(240) NOT NULL,
    content VARCHAR(3000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT uq_knowledge_chunk_order UNIQUE(document_id, chunk_order)
);

CREATE INDEX ix_knowledge_chunks_owner_doc ON task_os.knowledge_chunks(owner_id, document_id, chunk_order);
