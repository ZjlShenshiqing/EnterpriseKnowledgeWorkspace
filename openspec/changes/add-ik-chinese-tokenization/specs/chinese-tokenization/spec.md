## ADDED Requirements

### Requirement: IK query tokenization
The tokenizer SHALL use IK smart segmentation for query and reranker text.

#### Scenario: Chinese question tokenization
- **WHEN** the tokenizer receives a non-empty Chinese question in query mode
- **THEN** it SHALL return normalized lexical terms instead of treating the complete question as one token

### Requirement: IK document tokenization
The tokenizer SHALL use IK maximum-word segmentation for document chunk indexing.

#### Scenario: Document maximum-word segmentation
- **WHEN** the tokenizer receives Chinese document text in document mode
- **THEN** it SHALL retain fine-grained and overlapping terms produced by IK maximum-word segmentation

### Requirement: Token normalization
The tokenizer MUST remove blank and punctuation-only terms, lowercase Latin text using locale-independent rules, and preserve complete mixed ASCII identifiers containing separators.

#### Scenario: Mixed identifier preservation
- **WHEN** input contains `OA-2025-001`
- **THEN** the returned tokens SHALL contain `oa-2025-001` even if IK emits smaller component terms

#### Scenario: Empty input
- **WHEN** input is null, blank, or contains only punctuation
- **THEN** the tokenizer SHALL return an empty token list

### Requirement: Tokenization fallback
The tokenizer SHALL support configurable local fallback tokenization when IK segmentation fails.

#### Scenario: IK failure with fallback enabled
- **WHEN** IK segmentation fails and fallback is enabled
- **THEN** the tokenizer SHALL return deterministic Chinese character unigram/bigram and normalized ASCII terms and SHALL log the failure without logging source text

#### Scenario: IK failure with fallback disabled
- **WHEN** IK segmentation fails and fallback is disabled
- **THEN** the tokenizer SHALL propagate the tokenization failure
