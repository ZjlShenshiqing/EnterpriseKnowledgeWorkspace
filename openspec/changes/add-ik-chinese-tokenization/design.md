## Context

`LocalFeatureRagReranker` and `SparseVectorGenerator` currently own separate tokenizers. The reranker splits only on whitespace and punctuation, while sparse vectors use Chinese character unigrams and bigrams. The duplicated behavior weakens Chinese keyword features and makes query terms differ between retrieval and reranking.

The service runs on Java 17 and does not embed Elasticsearch. The selected dependency must therefore expose IK's standalone core API rather than an Elasticsearch analysis plugin.

## Goals / Non-Goals

**Goals:**

- Introduce one reusable tokenization contract for RAG components.
- Use IK smart segmentation for questions and reranker features.
- Use IK maximum-word segmentation for document sparse indexing.
- Preserve English words, numbers, and complete mixed identifiers.
- Keep retrieval available through deterministic local fallback tokenization.
- Make the sparse-token change operationally explicit through hybrid index rebuild guidance.

**Non-Goals:**

- Add Elasticsearch or OpenSearch.
- Implement BM25, IDF corpus statistics, synonym expansion, or stop-word dictionaries.
- Add custom IK dictionaries in the first iteration.
- Automatically rebuild indexes during service startup.
- Change dense embeddings or collection schemas.

## Decisions

### 1. Use standalone IK Analyzer core

Add `org.truenewx:ik-analyzer-core:5.1.0` to the knowledge service. The implementation will use `IKSegmenter` and `Lexeme` directly, avoiding any Elasticsearch plugin lifecycle or Lucene analyzer integration.

An Elasticsearch analysis plugin was rejected because the service does not run inside Elasticsearch. A remote segmentation service was rejected because tokenization is latency-sensitive and does not justify another network dependency.

### 2. Introduce a shared tokenizer boundary

Create a focused tokenizer abstraction with explicit query and document operations, for example:

```java
List<String> tokenizeQuery(String text);
List<String> tokenizeDocument(String text);
```

`IkChineseTokenizer` will be the Spring implementation. `LocalFeatureRagReranker` and `SparseVectorGenerator` will depend on the abstraction rather than IK classes.

This keeps IK replaceable, makes mode selection visible at call sites, and allows tests to verify integration without depending on IK internals.

### 3. Apply asymmetric segmentation modes

- Query and reranker tokenization uses IK smart mode to produce stable, lower-noise terms.
- Document sparse indexing uses IK maximum-word mode to retain overlapping candidate terms and improve exact-term recall.

Using maximum-word mode everywhere was rejected because query vectors would gain unnecessary overlapping terms. Using smart mode everywhere was rejected because document indexing would lose useful fine-grained terms.

### 4. Normalize and preserve identifiers

All emitted terms are trimmed and lowercased with `Locale.ROOT`; blank and punctuation-only terms are removed. In addition to IK terms, the tokenizer detects complete ASCII mixed identifiers containing separators, such as `OA-2025-001`, and adds the normalized full identifier if IK split it.

No stop-word filtering is added in this change. Stop words require a domain-specific list and effectiveness measurements; premature filtering could remove meaningful policy language.

### 5. Fall back locally when configured

Add configuration under `app.rag.tokenization` with IK enabled and fallback enabled by default. If IK throws while fallback is enabled, the tokenizer logs a warning without source text and returns deterministic character unigram/bigram plus normalized ASCII terms. If fallback is disabled, the error propagates.

The fallback is not expected during normal operation; it protects query availability from tokenizer initialization or dictionary failures.

### 6. Give sparse generation explicit query and document paths

`SparseVectorGenerator` will expose separate query and document generation methods. Query retrieval calls smart tokenization, while chunk writes and rebuilds call maximum-word tokenization. The existing hash dimension and normalized term-frequency weighting remain unchanged.

Keeping one ambiguous `generate` method was rejected because callers could silently use the wrong segmentation mode. Compatibility may be retained temporarily only where needed while all production call sites are migrated.

### 7. Rebuild through the existing administrative capability

After deployment, run the existing hybrid index rebuild endpoint/service for all successful, enabled documents. No startup rebuild is added. Environments using `VECTOR_ONLY` are unaffected until switching to hybrid mode, but their hybrid collection must still be rebuilt before that switch.

## Risks / Trade-offs

- **[Risk] IK dictionary choices do not match enterprise terminology** → Preserve full mixed identifiers now; add custom dictionaries only after evaluation identifies recurring misses.
- **[Risk] Maximum-word terms increase sparse vector size** → Keep the existing sparse dimension and measure vector term counts in tests; candidate limits remain unchanged.
- **[Risk] Query and stored vectors are incompatible during rollout** → Deploy code and rebuild hybrid indexes before evaluating or enabling hybrid traffic.
- **[Risk] Fallback terms differ from IK terms** → Fallback is availability protection, not an equivalent ranking path; emit warnings for operational visibility.
- **[Trade-off] TF hashing still lacks IDF semantics** → This change improves segmentation consistency but intentionally does not claim BM25 quality.

## Migration Plan

1. Add the IK dependency, shared tokenizer, configuration, and tests.
2. Integrate smart tokenization into reranking and sparse query generation.
3. Integrate maximum-word tokenization into sparse chunk writes and rebuilds.
4. Deploy with hybrid retrieval disabled or held from evaluation.
5. Rebuild hybrid indexes for existing successful documents using the existing rebuild capability.
6. Run fixed Chinese query comparisons, then enable or evaluate hybrid retrieval.

Rollback requires restoring the prior tokenizer code and rebuilding hybrid indexes again so stored sparse terms match the rolled-back query tokenizer.

## Open Questions

None for this iteration. Custom dictionaries, stop words, BM25 weighting, and cross-encoder reranking remain separate follow-up changes.
