## ADDED Requirements

### Requirement: Unified RAG retrieval service

The system SHALL provide a `RagRetrievalService` that is the single entry point for RAG document retrieval, encapsulating retrieval mode selection, vector/hybrid search execution, database query, permission final check, and result assembly.

#### Scenario: Vector-only retrieval matches current behavior

- **WHEN** `app.rag.retrieval.mode` is `VECTOR_ONLY` and retrieval is executed
- **THEN** the system performs dense vector search via the existing Milvus collection and returns the same results as the current `VectorSyncService.searchSimilar()` path

#### Scenario: Hybrid retrieval uses both dense and sparse

- **WHEN** `app.rag.retrieval.mode` is `HYBRID_MILVUS` and retrieval is executed
- **THEN** the system generates both dense and sparse query vectors, performs hybrid search against the hybrid collection, and returns fused results

#### Scenario: Fallback on hybrid failure

- **WHEN** `HYBRID_MILVUS` mode is active but the hybrid search call fails
- **THEN** the system SHALL fall back to `VECTOR_ONLY` mode for that request and log a warning

### Requirement: Permission final check via database

The system SHALL always perform a database-level permission check after Milvus retrieval, regardless of any Milvus metadata filters applied during search.

#### Scenario: Document deleted in DB is excluded

- **WHEN** a document returned by Milvus has `deleted = 1` in the database
- **THEN** that document and all its chunks SHALL be excluded from results

#### Scenario: Document disabled in DB is excluded

- **WHEN** a document returned by Milvus has `enabled = 0` in the database
- **THEN** that document and all its chunks SHALL be excluded from results

#### Scenario: Document status not SUCCESS is excluded

- **WHEN** a document returned by Milvus has a status other than `SUCCESS`
- **THEN** that document and all its chunks SHALL be excluded from results

#### Scenario: Chunk disabled in DB is excluded

- **WHEN** a chunk returned by Milvus has `enabled = 0` in the database
- **THEN** that chunk SHALL be excluded from results

#### Scenario: Document not visible to user is excluded

- **WHEN** a document returned by Milvus fails the `DocumentVisibilityService.canView()` check
- **THEN** that document and all its chunks SHALL be excluded from results

### Requirement: Result assembly preserves existing format

The system SHALL assemble retrieval results into the same structure currently used by `RagQaTool`: documents grouped with their matched chunks, each chunk containing `chunkIndex`, `text`, `score`, and `metadata`.

#### Scenario: Result format matches current output

- **WHEN** retrieval completes successfully
- **THEN** results contain document-level fields (`documentId`, `title`, `summary`, `fileType`, `fileName`, `fileSize`, `createdAt`, `metadata`) and per-chunk fields (`chunkIndex`, `text`, `score`, `metadata`)

#### Scenario: Duplicate chunks are deduplicated

- **WHEN** the same chunk appears in both dense and sparse search results
- **THEN** the chunk SHALL appear only once in the final result, with the fused/higher score

### Requirement: RagRetrievalService replaces direct VectorSyncService usage in RagQaTool

The `RagQaTool.execute()` method SHALL delegate retrieval to `RagRetrievalService.retrieve()` instead of directly calling `VectorSyncService.searchSimilar()` and performing inline DB queries and permission checks.

#### Scenario: RagQaTool delegates to retrieval service

- **WHEN** `RagQaTool.execute()` is called
- **THEN** it invokes `RagRetrievalService.retrieve(question, topK, user)` and maps the result to the MCP ToolResult format
