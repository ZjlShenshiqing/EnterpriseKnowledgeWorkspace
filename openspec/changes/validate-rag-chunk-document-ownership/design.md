## Context

`RagRetrievalServiceImpl` receives Milvus search rows containing a chunk ID and a `doc_id` stored in vector metadata. It loads documents by the returned document IDs, applies document visibility checks, and independently loads chunks by the returned chunk IDs. Candidate construction currently verifies that the document is visible and the chunk exists, but does not verify the relational invariant recorded in MySQL: `kb_document_chunk.document_id` must equal the Milvus `doc_id`.

MySQL is the authorization source of truth. Milvus is a derived search index and can become stale or inconsistent after partial writes, failed updates, manual rebuilds, or metadata defects. Vector metadata therefore cannot authorize access to chunk content.

## Goals / Non-Goals

**Goals:**

- Enforce chunk-to-document ownership using MySQL before chunk text enters reranking or result assembly.
- Fail closed for malformed, missing, or mismatched candidate relationships.
- Preserve valid retrieval ordering and the existing Tool/API response contract.
- Produce identifier-only diagnostics for index drift.
- Add focused regression coverage for the security boundary.

**Non-Goals:**

- Repair or delete inconsistent Milvus rows automatically.
- Change document visibility rules or permission types.
- Add Milvus schema fields, database columns, or migrations.
- Redesign multi-collection retrieval, hybrid search, or reranking.
- Expose mismatch details to end users.

## Decisions

### 1. MySQL ownership is authoritative

For every search result, candidate construction will parse `doc_id` and chunk ID, require the document to have passed visibility checks, load the chunk from MySQL, and require `Objects.equals(chunk.getDocumentId(), docId)` before reading its text into a rerank candidate.

Milvus metadata remains useful for locating candidates but is not trusted as an authorization relationship. The alternative of trusting the chunk ID alone and replacing the Milvus document ID with the database value is rejected because it could silently move an unauthorized candidate into another document context and conceal index corruption.

### 2. Invalid candidates are dropped independently

A missing chunk, null database `document_id`, or ownership mismatch removes only that candidate. Other valid candidates continue through reranking. This keeps retrieval available while enforcing fail-closed behavior at the candidate boundary.

Failing the entire request was considered but rejected because one stale vector row should not make all otherwise valid knowledge unavailable. The retrieval result is already allowed to contain fewer than the requested number of documents.

### 3. Validation occurs before candidate text is consumed

The ownership condition will be checked in `buildCandidates` before constructing `RerankedCandidate`. This ensures mismatched chunk text is not passed to local or future remote rerankers and cannot reach the LLM Tool result.

Validating only during final result assembly is rejected because rerankers would already have received unauthorized text.

### 4. Log identifiers without content

Ownership mismatches will produce a warning containing the Milvus chunk ID, Milvus document ID, and MySQL chunk document ID. The log will not contain chunk text, titles, user IDs, permission rows, or model context.

The warning supports diagnosis and future index reconciliation while avoiding additional sensitive-data exposure.

### 5. Test through the retrieval service boundary

Unit tests will exercise `RagRetrievalServiceImpl.retrieve` with mocked infrastructure dependencies and real candidate assembly behavior. Tests will prove that a mismatched candidate is absent from reranker input and the returned result, while valid candidates remain available.

Testing only a new private helper is rejected because it would not prove placement of the check before reranking.

## Risks / Trade-offs

- **[Risk] Existing inconsistent index rows produce fewer results** → This is intentional fail-closed behavior; warning logs identify rows needing rebuild.
- **[Risk] Identifier warnings can be noisy during large index drift** → Log once per rejected search candidate in this scoped change; add metrics or rate limiting separately if operational data shows a need.
- **[Risk] Mock-based unit tests do not validate a real Milvus response shape** → Preserve existing Milvus tests and keep the new tests focused on the authorization boundary; integration testing remains separate work.
- **[Trade-off] No automatic repair** → Avoids coupling a read path to destructive index mutation and keeps this security fix small and reversible.

## Migration Plan

1. Add failing retrieval-service regression tests for mismatched and valid ownership.
2. Add the ownership guard and identifier-only warning.
3. Run focused tests and the complete knowledge-service test suite.
4. Deploy without data migration. Existing invalid vector rows will be ignored immediately.

Rollback consists of reverting the guard. No stored data or API contract changes require rollback handling.

## Open Questions

None for this scoped fix. Index drift metrics and automated reconciliation can be proposed as a separate change after mismatch frequency is observed.
