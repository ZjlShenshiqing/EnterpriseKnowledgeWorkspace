## ADDED Requirements

### Requirement: Retrieval mode configuration

The system SHALL support a configuration property `app.rag.retrieval.mode` with values `VECTOR_ONLY` and `HYBRID_MILVUS`.

#### Scenario: Default mode is VECTOR_ONLY

- **WHEN** `app.rag.retrieval.mode` is not set
- **THEN** the system SHALL default to `VECTOR_ONLY`, preserving existing behavior

#### Scenario: HYBRID_MILVUS mode activates hybrid search

- **WHEN** `app.rag.retrieval.mode` is set to `HYBRID_MILVUS`
- **THEN** `RagRetrievalService` SHALL execute hybrid search against the hybrid collection instead of dense-only search

#### Scenario: Invalid mode value throws on startup

- **WHEN** `app.rag.retrieval.mode` is set to an unrecognized value
- **THEN** the application SHALL fail to start with a clear configuration error

### Requirement: Hybrid collection name configuration

The system SHALL support `app.milvus.hybrid-collection` configuration to specify the hybrid collection name.

#### Scenario: Hybrid collection name is configurable

- **WHEN** `app.milvus.hybrid-collection` is set to `kb_chunk_hybrid_v1`
- **THEN** all hybrid collection operations (create, write, search) SHALL use that collection name

#### Scenario: Default hybrid collection name

- **WHEN** `app.milvus.hybrid-collection` is not set
- **THEN** the system SHALL default to `kb_chunk_hybrid_v1`

### Requirement: RRF ranker configuration

The system SHALL support configurable RRF parameters for hybrid search fusion.

#### Scenario: Configurable top-N multiplier

- **WHEN** `app.rag.retrieval.top-n-multiplier` is set to `5`
- **THEN** each sub-search (dense and sparse) SHALL request `topK × 5` candidates before fusion

#### Scenario: Configurable RRF k parameter

- **WHEN** `app.rag.retrieval.ranker.rrf-k` is set to `60`
- **THEN** the RRF fusion SHALL use k=60 in the formula `1 / (k + rank)`

#### Scenario: Default RRF parameters

- **WHEN** ranker configuration is not set
- **THEN** the system SHALL use `topNMultiplier=5` and `rrfK=60`

### Requirement: Optional min-score threshold

The system SHALL support an optional minimum score threshold to filter low-confidence results after fusion.

#### Scenario: Min-score filtering is disabled by default

- **WHEN** `app.rag.retrieval.min-score.enabled` is not set
- **THEN** no min-score filtering SHALL be applied

#### Scenario: Results below threshold are filtered

- **WHEN** `app.rag.retrieval.min-score.enabled=true` and `app.rag.retrieval.min-score.value=0.3`
- **THEN** fused results with score below 0.3 SHALL be excluded
