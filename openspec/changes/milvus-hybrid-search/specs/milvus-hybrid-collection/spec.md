## ADDED Requirements

### Requirement: Hybrid collection schema with sparse vector support

The system SHALL support creating a new Milvus collection with schema: `id` (VarChar PK), `content` (VarChar), `metadata` (JSON), `dense_vector` (FloatVector), `sparse_vector` (SparseFloatVector).

#### Scenario: Hybrid collection is created on first use

- **WHEN** `ensureHybridCollectionLoaded(collectionName)` is called and the collection does not exist
- **THEN** a collection is created with all five fields, dense index (COSINE + AUTOINDEX), and sparse index (IP + SPARSE_INVERTED_INDEX)

#### Scenario: Hybrid collection is skipped if already exists

- **WHEN** `ensureHybridCollectionLoaded(collectionName)` is called and the collection already exists
- **THEN** the existing collection is loaded into memory without schema changes

#### Scenario: Existing collection is unaffected

- **WHEN** hybrid collection features are enabled
- **THEN** the existing `kb_chunk_embedding` collection and its schema SHALL remain unchanged

### Requirement: Hybrid vector write support

The system SHALL support writing chunk data with both dense and sparse vectors to the hybrid collection.

#### Scenario: Chunk with both vectors is written successfully

- **WHEN** `indexHybridChunks(collectionName, docId, chunks)` is called with chunks containing both `embedding` and `sparseVector`
- **THEN** all chunks are inserted into the hybrid collection with `id`, `content`, `metadata`, `dense_vector`, and `sparse_vector` fields populated

#### Scenario: Chunk missing sparse vector is rejected

- **WHEN** a chunk has `embedding` but `sparseVector` is null or empty
- **THEN** the system SHALL throw `BizException(PARAM_INVALID)` with a message indicating sparse vector is required for hybrid write

#### Scenario: Chunk with dimension mismatch is rejected

- **WHEN** a chunk's `embedding` length does not match the configured `denseDimension` or `sparseVector` dimensions are invalid
- **THEN** the system SHALL throw `BizException(PARAM_INVALID)`

### Requirement: Hybrid search API

The system SHALL support executing Milvus hybrid search with separate dense and sparse ANN requests fused by RRF.

#### Scenario: Hybrid search returns fused results

- **WHEN** `hybridSearch(collectionName, denseVector, sparseVector, topK, filter)` is called
- **THEN** the system submits a dense ANN request (topK × 5) and a sparse ANN request (topK × 5), fuses them via RRF (k=60), and returns the top-K results

#### Scenario: Dense-only match returns result

- **WHEN** a chunk is found by dense search but not by sparse search
- **THEN** the chunk SHALL still appear in the fused results if its RRF score warrants it

#### Scenario: Sparse-only match returns result

- **WHEN** a chunk is found by sparse search but not by dense search
- **THEN** the chunk SHALL still appear in the fused results if its RRF score warrants it

#### Scenario: Empty result when no matches

- **WHEN** neither dense nor sparse search returns any results
- **THEN** an empty list SHALL be returned

### Requirement: Metadata-based coarse filtering in Milvus

The system SHALL include permission-related fields in Milvus metadata and apply scalar filters during hybrid search.

#### Scenario: Only SUCCESS and enabled documents are searched

- **WHEN** any hybrid search is performed
- **THEN** the Milvus filter expression SHALL include `document_status == "SUCCESS"`, `document_enabled == true`, and `chunk_enabled == true`

#### Scenario: Department-level filter is applied

- **WHEN** user belongs to a specific department
- **THEN** the Milvus filter SHALL include department-level coarse filtering conditions as an optimization (not as a replacement for DB final check)
