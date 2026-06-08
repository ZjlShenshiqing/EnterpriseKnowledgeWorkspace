## ADDED Requirements

### Requirement: Database chunk ownership validation
The RAG retrieval service SHALL treat MySQL as the source of truth for chunk-to-document ownership and SHALL include a retrieved chunk only when its database `document_id` equals the `doc_id` returned by the search index.

#### Scenario: Matching chunk and document ownership
- **WHEN** a search result references a visible document and an enabled database chunk whose `document_id` equals the search result `doc_id`
- **THEN** the service SHALL allow the chunk to become a rerank candidate

#### Scenario: Mismatched chunk and document ownership
- **WHEN** a search result's chunk exists in MySQL but its database `document_id` differs from the search result `doc_id`
- **THEN** the service SHALL exclude the chunk before reranking and final result assembly

#### Scenario: Chunk ownership is missing
- **WHEN** a retrieved database chunk has a null `document_id`
- **THEN** the service SHALL exclude the chunk before reranking and final result assembly

### Requirement: Fail-closed candidate handling
The RAG retrieval service MUST reject invalid candidate relationships independently without exposing their chunk content and MUST continue processing other valid candidates.

#### Scenario: Mixed valid and mismatched candidates
- **WHEN** a retrieval result contains both a valid owned chunk and a mismatched chunk
- **THEN** the reranker SHALL receive only the valid chunk and the response SHALL contain only valid content

#### Scenario: All candidates are invalid
- **WHEN** every retrieved candidate is missing or fails the ownership validation
- **THEN** the service SHALL return an empty retrieval result without invoking the reranker

### Requirement: Ownership mismatch diagnostics
The service SHALL log ownership mismatches using identifiers required for index diagnosis and SHALL NOT log chunk text as part of the mismatch warning.

#### Scenario: Ownership mismatch warning
- **WHEN** a database chunk's `document_id` differs from the search result `doc_id`
- **THEN** the service SHALL emit a warning containing the chunk ID, search document ID, and database document ID without including the chunk text
