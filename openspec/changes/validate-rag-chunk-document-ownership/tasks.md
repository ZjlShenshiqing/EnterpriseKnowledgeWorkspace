## 1. Retrieval Security Tests

- [x] 1.1 Add a retrieval-service test proving a chunk whose database `document_id` differs from the Milvus `doc_id` is excluded before reranking.
- [x] 1.2 Add coverage proving mixed valid and mismatched candidates pass only valid chunk content to the reranker and response.
- [x] 1.3 Add coverage proving all invalid or missing candidates return an empty result without invoking the reranker.

## 2. Ownership Validation

- [x] 2.1 Add the fail-closed chunk-to-document ownership guard in `RagRetrievalServiceImpl.buildCandidates` before candidate construction.
- [x] 2.2 Add an identifier-only warning for ownership mismatches without logging chunk text or other content.

## 3. Verification

- [x] 3.1 Run the focused retrieval ownership test class and confirm the regression tests pass.
- [x] 3.2 Run all `enterprise-knowledge-ai-service` tests and confirm there are no regressions.
- [x] 3.3 Review the final diff against the OpenSpec requirements and confirm no API, schema, or unrelated behavior changes were introduced.
