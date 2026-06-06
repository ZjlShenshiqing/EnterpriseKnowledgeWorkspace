## ADDED Requirements

### Requirement: Sparse vector generation from chunk content

The system SHALL generate a sparse vector representation for each chunk's text content, suitable for keyword-based retrieval in Milvus hybrid search.

#### Scenario: BM25-based sparse vector is generated

- **WHEN** a chunk with text content is processed for hybrid indexing
- **THEN** a sparse vector (map of term index → weight) is generated from the chunk's content using a BM25 or term-frequency-based algorithm

#### Scenario: Empty content produces empty sparse vector

- **WHEN** chunk content is null or empty
- **THEN** the system SHALL produce an empty sparse vector and log a warning, rather than throwing an exception

#### Scenario: Chinese text is tokenized

- **WHEN** chunk content contains Chinese text
- **THEN** the sparse vector generator SHALL tokenize the text (using character n-grams or jieba segmentation) before computing term weights

### Requirement: Query-time sparse vector generation

The system SHALL generate a sparse vector for the user query using the same tokenization and weighting algorithm as document indexing.

#### Scenario: Query sparse vector matches indexing algorithm

- **WHEN** a query sparse vector is generated for hybrid search
- **THEN** it SHALL use the same tokenizer and weighting scheme as the indexing-time sparse vector generator

#### Scenario: Short query is handled correctly

- **WHEN** a query is very short (e.g., 1-3 characters)
- **THEN** the sparse vector SHALL still be generated with appropriate term weights

### Requirement: Sparse vector generation is encapsulated

The sparse vector generation logic SHALL be encapsulated in a dedicated component (`SparseVectorGenerator`), separate from the remote embedding service.

#### Scenario: SparseVectorGenerator is independently usable

- **WHEN** `SparseVectorGenerator.generate(text)` is called
- **THEN** it returns a sparse vector without calling any remote API

#### Scenario: Sparse generation does not affect EmbeddingService

- **WHEN** sparse vector generation is added
- **THEN** the existing `EmbeddingService` interface and its `BailianEmbeddingService` implementation SHALL remain unchanged
