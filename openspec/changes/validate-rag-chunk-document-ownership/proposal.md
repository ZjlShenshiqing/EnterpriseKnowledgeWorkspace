## Why

RAG retrieval currently trusts the `doc_id` and chunk ID pair returned by Milvus independently. If vector metadata is stale, corrupted, or inconsistent with MySQL, a chunk belonging to an unauthorized document can be attached to an authorized document and exposed to the reranker or LLM context.

## What Changes

- Add a fail-closed ownership check requiring each database chunk's `document_id` to equal the Milvus result's `doc_id`.
- Drop mismatched candidates before reranking and result assembly.
- Emit a warning with non-content identifiers when an ownership mismatch is detected, so index drift can be diagnosed without logging document text.
- Add regression tests covering valid ownership, mismatched ownership, missing chunks, and mixed candidate lists.
- Keep the existing RAG Tool/API response contract and Milvus schema unchanged.

## Capabilities

### New Capabilities

- `rag-retrieval-ownership-validation`: Validates the database ownership relationship between every retrieved chunk and document before content can enter reranking or model context.

### Modified Capabilities

None.

## Impact

- Affected service: `enterprise-knowledge-ai-service`.
- Primary code: `RagRetrievalServiceImpl` candidate construction and its unit tests.
- Runtime behavior: inconsistent Milvus candidates are ignored and logged; valid candidates are unchanged.
- No database migration, Milvus collection migration, dependency change, or external API change is required.
