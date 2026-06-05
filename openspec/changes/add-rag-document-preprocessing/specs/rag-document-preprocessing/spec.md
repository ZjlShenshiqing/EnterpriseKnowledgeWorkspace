## ADDED Requirements

### Requirement: Preprocess parsed documents before chunking
The system SHALL run a document preprocessing step after Tika parsing succeeds and before any chunking strategy is invoked.

#### Scenario: Tika text is normalized before paragraph chunking
- **WHEN** a user starts chunking for a document whose Tika extraction returns non-empty text
- **THEN** the system MUST pass the preprocessed chunk input text to the selected `PARAGRAPH` or `FIXED_SIZE` chunking strategy

#### Scenario: Empty parsed text still fails
- **WHEN** Tika extraction returns empty or blank text
- **THEN** the system MUST fail the chunk task with the existing document failure behavior

### Requirement: Default preprocessing works for all document formats
The system SHALL provide a default preprocessor that can handle any supported parsed document format without requiring the original file to be Markdown.

#### Scenario: Non-Markdown document uses default preprocessing
- **WHEN** a PDF, Word, Excel, PPT, HTML, TXT, or Markdown document is parsed successfully
- **THEN** the system MUST produce standardized chunk input using the default preprocessor unless a more specific preprocessor is selected

#### Scenario: Missing optional metadata does not fail preprocessing
- **WHEN** parsed metadata does not contain title, source, page, or document type fields
- **THEN** the system MUST still produce chunk input from the parsed text and available document fields

### Requirement: Preprocessed chunk input includes concise context
The default preprocessor SHALL add concise document-level context before the body text so retrieved chunks retain source meaning.

#### Scenario: Context header is included
- **WHEN** a document has a file name and optional source location
- **THEN** the preprocessed chunk input MUST include a concise context header containing the document title or file name and the source when available

#### Scenario: Body text is preserved
- **WHEN** the default preprocessor creates chunk input
- **THEN** the output MUST preserve the parsed body text content after the context header

### Requirement: Preprocessing metadata is persisted with chunks
The system SHALL merge preprocessing metadata into chunk metadata persisted in `kb_document_chunk.metadata_json` and vector metadata.

#### Scenario: Chunk metadata contains preprocessing fields
- **WHEN** chunks are generated from preprocessed input
- **THEN** each chunk metadata MUST include available preprocessing fields such as `doc_type`, `title`, `section_path`, `source_location`, and `preprocess_strategy`

#### Scenario: Existing chunk metadata remains compatible
- **WHEN** preprocessing metadata is merged into chunk metadata
- **THEN** existing metadata fields such as `docId`, `fileName`, `chunkIndex`, `startOffset`, `endOffset`, and `sensitivityLevel` MUST remain present

### Requirement: Preprocessing remains compatible with existing document lifecycle
The preprocessing step SHALL preserve the existing document status lifecycle and chunk task logging behavior.

#### Scenario: Successful preprocessing follows existing success path
- **WHEN** parsing, preprocessing, chunking, embedding, and persistence complete successfully
- **THEN** the document MUST transition to `SUCCESS` and record chunk count and durations using the existing chunk log mechanism

#### Scenario: Preprocessing failure follows existing failure path
- **WHEN** preprocessing throws a business or system exception
- **THEN** the document MUST transition to `FAILED` and the chunk log MUST record the failure message

### Requirement: Preprocessor selection has a default fallback
The system SHALL select a document preprocessor through an extensible mechanism and MUST fall back to the default preprocessor when no type-specific preprocessor matches.

#### Scenario: No type-specific preprocessor exists
- **WHEN** a document has no configured or matched type-specific preprocessor
- **THEN** the system MUST use the default preprocessor

#### Scenario: Future type-specific preprocessor can be added
- **WHEN** a new document type preprocessor is registered later
- **THEN** the selection mechanism MUST allow it to handle matching documents without modifying existing chunking strategies
