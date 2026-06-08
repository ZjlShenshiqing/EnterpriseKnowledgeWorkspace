## 1. IK Tokenizer Foundation

- [x] 1.1 Add the standalone IK Analyzer core dependency and tokenization configuration properties.
- [x] 1.2 Add failing tests for smart Chinese query terms, maximum-word document terms, identifier normalization, empty input, and fallback behavior.
- [x] 1.3 Implement the shared tokenizer contract and IK-backed tokenizer until the focused tests pass.

## 2. Local Reranker Integration

- [x] 2.1 Add failing reranker tests proving Chinese questions use multiple shared query tokens and improve subset keyword coverage.
- [x] 2.2 Inject the shared tokenizer into `LocalFeatureRagReranker` and remove its punctuation-only tokenizer.
- [x] 2.3 Run focused reranker tests and update existing construction sites without changing scoring weights.

## 3. Sparse Vector Integration

- [x] 3.1 Add failing sparse-vector tests proving query generation uses smart tokens and document generation uses maximum-word tokens.
- [x] 3.2 Split sparse generation into explicit query and document methods and migrate hybrid search to query mode.
- [x] 3.3 Migrate chunk insert, update, sync, and hybrid rebuild paths to document mode.
- [x] 3.4 Run focused sparse and vector-sync tests and confirm identifier terms remain available.

## 4. Migration And Verification

- [x] 4.1 Document that all existing hybrid indexes must be rebuilt through the existing administrative rebuild capability after deployment.
- [x] 4.2 Run the full `enterprise-knowledge-ai-service` test suite.
- [x] 4.3 Run OpenSpec strict validation and review the final diff for API, database, and Milvus schema compatibility.
