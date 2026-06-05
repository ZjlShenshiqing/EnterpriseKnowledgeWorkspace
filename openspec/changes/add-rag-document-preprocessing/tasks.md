## 1. Preprocessing Model And SPI

- [x] 1.1 Add a preprocessing result model that carries chunk input text, document metadata, and chunk metadata defaults
- [x] 1.2 Add a preprocessing context model containing `KbDocument`, parsed text, and Tika metadata
- [x] 1.3 Add `DocumentPreprocessor` interface with matching and preprocessing methods
- [x] 1.4 Add a selector service that chooses a matching preprocessor and falls back to the default implementation

## 2. Default Preprocessor

- [x] 2.1 Implement `DefaultDocumentPreprocessor` with a concise context header and preserved body text
- [x] 2.2 Normalize missing optional fields so preprocessing succeeds when title, source, page, or document type metadata is absent
- [x] 2.3 Populate metadata fields such as `doc_type`, `title`, `source_location`, `section_path`, and `preprocess_strategy`

## 3. Chunk Pipeline Integration

- [x] 3.1 Update `DocumentChunkingServiceImpl` to call preprocessing after Tika extraction and before chunk strategy execution
- [x] 3.2 Ensure `PARAGRAPH` and `FIXED_SIZE` receive the preprocessed chunk input text
- [x] 3.3 Keep empty Tika text failure behavior unchanged
- [x] 3.4 Preserve existing `PENDING` → `RUNNING` → `SUCCESS` / `FAILED` lifecycle and chunk log updates

## 4. Metadata Persistence

- [x] 4.1 Merge preprocessing document metadata into `KbDocument.metadata`
- [x] 4.2 Merge preprocessing chunk metadata into each `ChunkMetadata` / `VectorDocChunk.metadata`
- [x] 4.3 Verify `kb_document_chunk.metadata_json` still includes existing fields such as `docId`, `fileName`, `chunkIndex`, offsets, and sensitivity
- [x] 4.4 Verify Milvus vector metadata receives the merged preprocessing metadata without schema changes

## 5. Tests

- [x] 5.1 Add unit tests for default preprocessing output and metadata fallback behavior
- [x] 5.2 Add service tests proving `DocumentChunkingServiceImpl` chunks preprocessed text instead of raw Tika text
- [x] 5.3 Add compatibility tests for existing paragraph and fixed-size chunk behavior
- [x] 5.4 Add failure-path tests for preprocessing exceptions and blank parsed text

## 6. Documentation And Validation

- [x] 6.1 Update knowledge service documentation to describe the preprocessing layer and default template
- [x] 6.2 Run the relevant knowledge-ai-service test suite
- [x] 6.3 Run `openspec status --change "add-rag-document-preprocessing"` and confirm the change is apply-ready
