## Why

The local RAG reranker currently splits text only on whitespace and punctuation, so a complete Chinese question often becomes one token and produces weak keyword-coverage features. Sparse retrieval uses a separate character unigram/bigram tokenizer, creating inconsistent query semantics between retrieval and reranking.

## What Changes

- Add a shared Chinese tokenizer backed by IK Analyzer.
- Use IK smart segmentation for user queries and local reranking.
- Use IK maximum-word segmentation for document chunk sparse indexing.
- Preserve normalized English words, numbers, and mixed identifiers such as `OA-2025-001`.
- Add a configurable fallback to the existing local tokenization behavior when IK segmentation fails.
- Rebuild the hybrid sparse index after deployment because tokenization changes alter stored sparse vectors.

## Capabilities

### New Capabilities

- `chinese-tokenization`: Provides reusable IK-based smart and maximum-word segmentation with normalization and fallback behavior.
- `rag-tokenization-integration`: Applies query/document segmentation modes consistently to local reranking and sparse vector generation.

### Modified Capabilities

None.

## Impact

- Affected service: `enterprise-knowledge-ai-service`.
- Adds an IK Analyzer core Maven dependency.
- Changes local rerank keyword and title matching behavior.
- Changes sparse vector terms for hybrid search and therefore requires hybrid index rebuild.
- No REST/MCP API, database schema, or Milvus collection schema changes.
