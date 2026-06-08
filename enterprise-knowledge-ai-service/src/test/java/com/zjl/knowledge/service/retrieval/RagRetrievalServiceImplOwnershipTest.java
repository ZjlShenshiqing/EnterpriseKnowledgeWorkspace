package com.zjl.knowledge.service.retrieval;

import com.zjl.knowledge.config.RagRetrievalProperties;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.mapper.KbDocumentPermissionMapper;
import com.zjl.knowledge.milvus.SearchResult;
import com.zjl.knowledge.service.DocumentVisibilityService;
import com.zjl.knowledge.service.VectorSyncService;
import com.zjl.knowledge.service.rerank.RagRerankService;
import com.zjl.knowledge.service.rerank.RerankRequest;
import com.zjl.knowledge.service.rerank.RerankedCandidate;
import com.zjl.knowledge.web.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * RAG 检索 Chunk 与文档归属关系安全测试。
 */
@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceImplOwnershipTest {

    @Mock
    private VectorSyncService vectorSyncService;

    @Mock
    private KbDocumentMapper kbDocumentMapper;

    @Mock
    private KbDocumentChunkMapper kbDocumentChunkMapper;

    @Mock
    private KbDocumentPermissionMapper kbDocumentPermissionMapper;

    @Mock
    private DocumentVisibilityService documentVisibilityService;

    @Mock
    private RagRerankService ragRerankService;

    private RagRetrievalServiceImpl service;
    private UserContext user;

    @BeforeEach
    void setUp() {
        service = new RagRetrievalServiceImpl(
                vectorSyncService,
                kbDocumentMapper,
                kbDocumentChunkMapper,
                kbDocumentPermissionMapper,
                documentVisibilityService,
                new RagRetrievalProperties(),
                ragRerankService
        );
        user = UserContext.builder().userId(10L).build();
    }

    @Test
    void excludesChunkWhenDatabaseDocumentIdDiffersFromSearchDocumentId() {
        KbDocument document = searchableDocument(1001L);
        KbDocumentChunk mismatchedChunk = chunk(2001L, 9001L, "unauthorized content", 0);
        stubVisibleDocuments(List.of(document));
        when(vectorSyncService.searchSimilar(eq("question"), eq(15), any(KbDocument.class)))
                .thenReturn(List.of(searchResult(2001L, 1001L)));
        when(kbDocumentChunkMapper.selectBatchIds(anyCollection())).thenReturn(List.of(mismatchedChunk));

        RetrievalResult result = service.retrieve("question", 5, user);

        assertThat(result.documents()).isEmpty();
        verify(ragRerankService, never()).rerank(any());
    }

    @Test
    void sendsOnlyOwnedChunksToRerankerWhenCandidatesAreMixed() {
        KbDocument document = searchableDocument(1001L);
        KbDocumentChunk validChunk = chunk(2001L, 1001L, "authorized content", 0);
        KbDocumentChunk mismatchedChunk = chunk(2002L, 9001L, "unauthorized content", 1);
        stubVisibleDocuments(List.of(document));
        when(vectorSyncService.searchSimilar(eq("question"), eq(15), any(KbDocument.class)))
                .thenReturn(List.of(searchResult(2001L, 1001L), searchResult(2002L, 1001L)));
        when(kbDocumentChunkMapper.selectBatchIds(anyCollection()))
                .thenReturn(List.of(validChunk, mismatchedChunk));
        when(ragRerankService.rerank(any())).thenAnswer(invocation ->
                invocation.<RerankRequest>getArgument(0).candidates());

        RetrievalResult result = service.retrieve("question", 5, user);

        ArgumentCaptor<RerankRequest> requestCaptor = ArgumentCaptor.forClass(RerankRequest.class);
        verify(ragRerankService).rerank(requestCaptor.capture());
        List<RerankedCandidate> rerankCandidates = requestCaptor.getValue().candidates();
        assertThat(rerankCandidates)
                .extracting(RerankedCandidate::text)
                .containsExactly("authorized content");
        assertThat(result.documents()).hasSize(1);
        assertThat(result.documents().get(0).matchedChunks())
                .extracting(RetrievalResult.ChunkResult::text)
                .containsExactly("authorized content");
    }

    @Test
    void returnsEmptyResultWithoutRerankingWhenChunkIsMissing() {
        KbDocument document = searchableDocument(1001L);
        stubVisibleDocuments(List.of(document));
        when(vectorSyncService.searchSimilar(eq("question"), eq(15), any(KbDocument.class)))
                .thenReturn(List.of(searchResult(2001L, 1001L)));
        when(kbDocumentChunkMapper.selectBatchIds(anyCollection())).thenReturn(List.of());

        RetrievalResult result = service.retrieve("question", 5, user);

        assertThat(result.documents()).isEmpty();
        verify(ragRerankService, never()).rerank(any());
    }

    @Test
    void excludesChunkWhenDatabaseDocumentIdIsNull() {
        KbDocument document = searchableDocument(1001L);
        KbDocumentChunk chunkWithoutOwner = chunk(2001L, null, "unowned content", 0);
        stubVisibleDocuments(List.of(document));
        when(vectorSyncService.searchSimilar(eq("question"), eq(15), any(KbDocument.class)))
                .thenReturn(List.of(searchResult(2001L, 1001L)));
        when(kbDocumentChunkMapper.selectBatchIds(anyCollection())).thenReturn(List.of(chunkWithoutOwner));

        RetrievalResult result = service.retrieve("question", 5, user);

        assertThat(result.documents()).isEmpty();
        verify(ragRerankService, never()).rerank(any());
    }

    private void stubVisibleDocuments(List<KbDocument> documents) {
        when(kbDocumentMapper.selectBatchIds(anyCollection())).thenReturn(documents);
        when(kbDocumentPermissionMapper.selectList(any())).thenReturn(List.of());
        when(documentVisibilityService.canView(any(), any(), any())).thenReturn(true);
    }

    private KbDocument searchableDocument(Long id) {
        KbDocument document = new KbDocument();
        document.setId(id);
        document.setTitle("document-" + id);
        document.setStatus(DocumentStatus.SUCCESS.name());
        document.setEnabled(1);
        document.setDeleted(0);
        document.setMetadata("{}");
        return document;
    }

    private KbDocumentChunk chunk(Long id, Long documentId, String text, int index) {
        KbDocumentChunk chunk = new KbDocumentChunk();
        chunk.setId(id);
        chunk.setDocumentId(documentId);
        chunk.setChunkText(text);
        chunk.setChunkIndex(index);
        chunk.setEnabled(1);
        return chunk;
    }

    private SearchResult searchResult(Long chunkId, Long documentId) {
        return new SearchResult(
                String.valueOf(chunkId),
                String.valueOf(documentId),
                0.9f,
                Map.of("doc_id", String.valueOf(documentId))
        );
    }
}
