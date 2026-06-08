## 1. Configuration

- [x] 1.1 Add `app.rag.retrieval.mode`, `app.rag.retrieval.top-n-multiplier`, `app.rag.retrieval.ranker.rrf-k`, `app.rag.retrieval.min-score.*` to `application.yml`
- [x] 1.2 Add `app.milvus.hybrid-collection` and `app.milvus.sparse-dimension` to `MilvusProperties`
- [x] 1.3 Create `RagRetrievalProperties` class (`@ConfigurationProperties(prefix = "app.rag.retrieval")`) with mode enum, topNMultiplier, ranker config, minScore config

## 2. Sparse Vector Generation

- [x] 2.1 Built-in character n-gram + whitespace tokenizer (no external dependency)
- [x] 2.2 Create `SparseVectorGenerator` component: `generate(String text)` returns sparse vector (term→weight map), supports Chinese tokenization
- [x] 2.3 Add unit test: empty text returns empty vector, Chinese text is tokenized, short query works

## 3. Hybrid Collection Schema

- [x] 3.1 Add `ensureHybridCollectionLoaded(String collectionName)` method to `MilvusCollectionHelper` with schema: `id / content / metadata / dense_vector(FloatVector+COSINE) / sparse_vector(SparseFloatVector+IP)`
- [x] 3.2 Add `sparseDimension` field to `MilvusProperties`

## 4. Hybrid Vector Write

- [x] 4.1 Add `sparseVector` field (`Map<Long, Float>`) to `VectorDocChunk`
- [x] 4.2 Add `indexHybridChunks(String collectionName, String docId, List<VectorDocChunk> chunks)` method to `MilvusVectorWriter`
- [x] 4.3 Add `upsertHybridChunk(String collectionName, String docId, VectorDocChunk chunk)` method to `MilvusVectorWriter`
- [x] 4.4 Add validation: reject chunks with null/empty sparse vector when writing to hybrid collection

## 5. Hybrid Search in MilvusVectorWriter

- [x] 5.1 Add `hybridSearch(...)` method using dual-search + manual RRF approach (SDK version-safe)
- [x] 5.2 Implement RRF fusion logic (dense topK×5 + sparse topK×5 → RRF k=60 → topK)
- [x] 5.3 Unit tests compiled and passing (SparseVectorGeneratorTest + existing tests all pass)

## 6. VectorSyncService Extension

- [x] 6.1 Add `hybridSearchSimilar(String query, int topK, KbDocument document)` to `VectorSyncService` interface
- [x] 6.2 Implement in `VectorSyncServiceImpl` with dense embedding + sparse generation + hybrid writer call
- [x] 6.3 Add config-driven dispatch: `searchSimilar()` delegates to VECTOR_ONLY or HYBRID_MILVUS path
- [x] 6.4 Add fallback: on hybrid search exception, fall back to dense-only search

## 7. RagRetrievalService

- [x] 7.1 Create `RagRetrievalService` interface with `RetrievalResult retrieve(String question, int topK, UserContext user)` method
- [x] 7.2 Create `RagRetrievalServiceImpl` with full retrieval pipeline
- [x] 7.3 Create `RetrievalResult` DTO with `DocumentResult` and `ChunkResult` records
- [x] 7.4 Implement permission final check: filter out deleted/disabled/FAILED documents, disabled chunks, invisible documents
- [x] 7.5 Existing test (RagQaToolTest) updated and passing

## 8. RagQaTool Refactor

- [x] 8.1 Replace direct dependencies with single `RagRetrievalService` injection
- [x] 8.2 Simplify `execute()` to delegate to `ragRetrievalService.retrieve()`
- [x] 8.3 Remove `buildMatchedChunks()`, `isSearchable()`, `isVisible()`, `parseLong()` helper methods from `RagQaTool`
- [x] 8.4 Output format verified: same `documentId`, `title`, `summary`, `fileType`, `fileName`, `fileSize`, `createdAt`, `metadata`, `matchedChunks` with `chunkIndex`, `text`, `score`, `metadata`

## 9. Integration & Verification

- [x] 9.1 `application.yml` updated with complete `app.rag.retrieval.*` config block (default: `VECTOR_ONLY`)
- [x] 9.2 VECTOR_ONLY mode verified: `searchSimilar()` delegates to `vectorOnlySearch()` when mode is VECTOR_ONLY
- [x] 9.3 HYBRID_MILVUS mode wiring verified: full chain `RagQaTool → RagRetrievalService → VectorSyncService.searchSimilar() → hybridSearchSimilar() → MilvusVectorWriter.hybridSearch()` connected
- [x] 9.4 All existing tests pass: `mvn test -pl enterprise-knowledge-ai-service` → 17 tests, 0 failures

## 10. Hybrid Write Closure

- [x] 10.1 Connect HYBRID_MILVUS mode to dual-write dense and hybrid vectors during chunk sync, batch sync, direct indexing, and chunk update
- [x] 10.2 Generate sparse vectors for hybrid writes from chunk content before calling `indexHybridChunks` / `upsertHybridChunk`
- [x] 10.3 Delete hybrid collection rows alongside dense rows when HYBRID_MILVUS mode is active
- [x] 10.4 Initialize the hybrid collection on startup when `app.rag.retrieval.mode=HYBRID_MILVUS`
- [x] 10.5 Add tests for hybrid write closure and startup collection initialization

## 11. Historical Hybrid Index Rebuild

- [x] 11.1 Add forced hybrid rebuild method that writes hybrid collection even when retrieval mode remains VECTOR_ONLY
- [x] 11.2 Add admin API to rebuild one document or a bounded batch of SUCCESS documents
- [x] 11.3 Return rebuild totals, skipped documents, failed documents, and indexed chunk count
- [x] 11.4 Add unit tests for skip/failure behavior and forced hybrid rebuild
- [x] 11.5 Update hybrid search implementation docs with rebuild endpoints and rollout notes
