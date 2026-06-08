package com.zjl.knowledge.service.impl;

import com.zjl.knowledge.config.MilvusProperties;
import com.zjl.knowledge.config.RagRetrievalProperties;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.embedding.EmbeddingService;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.milvus.ChunkVectorStore;
import com.zjl.knowledge.milvus.MilvusVectorWriter;
import com.zjl.knowledge.milvus.SearchResult;
import com.zjl.knowledge.milvus.SparseVectorGenerator;
import com.zjl.knowledge.milvus.VectorDocChunk;
import com.zjl.knowledge.service.KbMilvusRoutingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 向量同步服务 hybrid 写入测试。
 */
@ExtendWith(MockitoExtension.class)
class VectorSyncServiceImplHybridTest {

    @Mock
    private EmbeddingService embeddingService;

    @Mock
    private ChunkVectorStore chunkVectorStore;

    @Mock
    private KbMilvusRoutingService kbMilvusRoutingService;

    @Mock
    private SparseVectorGenerator sparseVectorGenerator;

    @Mock
    private MilvusVectorWriter milvusVectorWriter;

    private RagRetrievalProperties retrievalProperties;

    private MilvusProperties milvusProperties;

    private VectorSyncServiceImpl service;

    @BeforeEach
    void setUp() {
        retrievalProperties = new RagRetrievalProperties();
        milvusProperties = new MilvusProperties();
        milvusProperties.setHybridCollection("kb_chunk_hybrid_v1");
        service = new VectorSyncServiceImpl(
                embeddingService,
                chunkVectorStore,
                kbMilvusRoutingService,
                sparseVectorGenerator,
                milvusVectorWriter,
                retrievalProperties,
                milvusProperties
        );
    }

    @Test
    void indexDocumentChunksDoesNotWriteHybridCollectionInVectorOnlyMode() {
        KbDocument document = document();
        VectorDocChunk chunk = vectorChunk();
        when(kbMilvusRoutingService.collectionForVectorWrite(document)).thenReturn("kb_chunk_embedding");

        service.indexDocumentChunks(document, List.of(chunk));

        verify(chunkVectorStore).indexDocumentChunks("kb_chunk_embedding", 1001L, List.of(chunk));
        verify(milvusVectorWriter, never()).indexHybridChunks(eq("kb_chunk_hybrid_v1"), eq("1001"), anyList());
    }

    @Test
    void indexDocumentChunksWritesHybridCollectionWithSparseVectorInHybridMode() {
        retrievalProperties.setMode(RagRetrievalProperties.RetrievalMode.HYBRID_MILVUS);
        KbDocument document = document();
        VectorDocChunk chunk = vectorChunk();
        when(kbMilvusRoutingService.collectionForVectorWrite(document)).thenReturn("kb_chunk_embedding");
        when(sparseVectorGenerator.generateDocument("差旅报销材料")).thenReturn(Map.of(12L, 1.0f));

        service.indexDocumentChunks(document, List.of(chunk));

        verify(chunkVectorStore).indexDocumentChunks("kb_chunk_embedding", 1001L, List.of(chunk));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VectorDocChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(milvusVectorWriter).indexHybridChunks(eq("kb_chunk_hybrid_v1"), eq("1001"), chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).hasSize(1);
        assertThat(chunksCaptor.getValue().get(0).getSparseVector()).containsEntry(12L, 1.0f);
        assertThat(chunksCaptor.getValue().get(0).getEmbedding()).containsExactly(0.1f, 0.2f);
        assertThat(chunksCaptor.getValue().get(0).getMetadata())
                .containsEntry("document_status", DocumentStatus.SUCCESS.name())
                .containsEntry("document_enabled", true)
                .containsEntry("chunk_enabled", true);
    }

    @Test
    void syncChunksBuildsDenseAndSparseVectorsForHybridWrite() {
        retrievalProperties.setMode(RagRetrievalProperties.RetrievalMode.HYBRID_MILVUS);
        KbDocument document = document();
        document.setStatus("SUCCESS");
        document.setEnabled(1);
        KbDocumentChunk chunk = new KbDocumentChunk();
        chunk.setId(2001L);
        chunk.setChunkText("OA-2025-001");
        chunk.setChunkIndex(0);
        chunk.setEnabled(1);

        when(kbMilvusRoutingService.embeddingModelOrDefault(document)).thenReturn("");
        when(kbMilvusRoutingService.collectionForVectorWrite(document)).thenReturn("kb_chunk_embedding");
        when(embeddingService.embedBatch(List.of("OA-2025-001"))).thenReturn(List.of(List.of(0.3f, 0.4f)));
        when(sparseVectorGenerator.generateDocument("OA-2025-001")).thenReturn(Map.of(99L, 1.0f));

        service.syncChunks(document, List.of(chunk));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VectorDocChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(milvusVectorWriter).indexHybridChunks(eq("kb_chunk_hybrid_v1"), eq("1001"), chunksCaptor.capture());
        VectorDocChunk hybridChunk = chunksCaptor.getValue().get(0);
        assertThat(hybridChunk.getChunkId()).isEqualTo("2001");
        assertThat(hybridChunk.getSparseVector()).containsEntry(99L, 1.0f);
        assertThat(hybridChunk.getMetadata())
                .containsEntry("document_status", "SUCCESS")
                .containsEntry("document_enabled", true)
                .containsEntry("chunk_enabled", true);
    }

    @Test
    void updateChunkUpsertsHybridCollectionInHybridMode() {
        retrievalProperties.setMode(RagRetrievalProperties.RetrievalMode.HYBRID_MILVUS);
        KbDocument document = document();
        KbDocumentChunk chunk = new KbDocumentChunk();
        chunk.setId(2001L);
        chunk.setChunkText("制度编号");
        chunk.setChunkIndex(2);

        when(kbMilvusRoutingService.embeddingModelOrDefault(document)).thenReturn("");
        when(kbMilvusRoutingService.collectionForVectorWrite(document)).thenReturn("kb_chunk_embedding");
        when(embeddingService.embed("制度编号")).thenReturn(List.of(0.5f, 0.6f));
        when(sparseVectorGenerator.generateDocument("制度编号")).thenReturn(Map.of(31L, 1.0f));

        service.updateChunk(document, chunk);

        ArgumentCaptor<VectorDocChunk> chunkCaptor = ArgumentCaptor.forClass(VectorDocChunk.class);
        verify(milvusVectorWriter).upsertHybridChunk(eq("kb_chunk_hybrid_v1"), eq("1001"), chunkCaptor.capture());
        assertThat(chunkCaptor.getValue().getSparseVector()).containsEntry(31L, 1.0f);
    }

    @Test
    void rebuildHybridChunksWritesHybridCollectionEvenInVectorOnlyMode() {
        KbDocument document = document();
        document.setStatus(DocumentStatus.SUCCESS.name());
        document.setEnabled(1);
        KbDocumentChunk chunk = new KbDocumentChunk();
        chunk.setId(2001L);
        chunk.setChunkText("历史制度编号");
        chunk.setChunkIndex(0);
        chunk.setEnabled(1);

        when(kbMilvusRoutingService.embeddingModelOrDefault(document)).thenReturn("");
        when(embeddingService.embedBatch(List.of("历史制度编号"))).thenReturn(List.of(List.of(0.7f, 0.8f)));
        when(sparseVectorGenerator.generateDocument("历史制度编号")).thenReturn(Map.of(77L, 1.0f));

        service.rebuildHybridChunks(document, List.of(chunk));

        verify(milvusVectorWriter).deleteByDocumentId("kb_chunk_hybrid_v1", "1001");
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<VectorDocChunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(milvusVectorWriter).indexHybridChunks(eq("kb_chunk_hybrid_v1"), eq("1001"), chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).hasSize(1);
        assertThat(chunksCaptor.getValue().get(0).getSparseVector()).containsEntry(77L, 1.0f);
    }

    @Test
    void hybridSearchUsesQuerySparseTokenizationMode() {
        retrievalProperties.setMode(RagRetrievalProperties.RetrievalMode.HYBRID_MILVUS);
        KbDocument document = document();
        when(kbMilvusRoutingService.embeddingModelOrDefault(document)).thenReturn("");
        when(embeddingService.embed("差旅报销材料")).thenReturn(List.of(0.1f, 0.2f));
        when(sparseVectorGenerator.generateQuery("差旅报销材料")).thenReturn(Map.of(12L, 1.0f));
        when(milvusVectorWriter.hybridSearch(
                eq("kb_chunk_hybrid_v1"),
                org.mockito.ArgumentMatchers.any(float[].class),
                eq(Map.of(12L, 1.0f)),
                eq(5),
                org.mockito.ArgumentMatchers.anyString(),
                eq(60),
                eq(5)))
                .thenReturn(List.of(new SearchResult("2001", "1001", 0.9f, Map.of())));

        List<SearchResult> results = service.hybridSearchSimilar("差旅报销材料", 5, document);

        assertThat(results).hasSize(1);
        verify(sparseVectorGenerator).generateQuery("差旅报销材料");
        verify(sparseVectorGenerator, never()).generateDocument("差旅报销材料");
    }

    private KbDocument document() {
        KbDocument document = new KbDocument();
        document.setId(1001L);
        return document;
    }

    private VectorDocChunk vectorChunk() {
        return VectorDocChunk.builder()
                .chunkId("2001")
                .content("差旅报销材料")
                .index(0)
                .embedding(new float[]{0.1f, 0.2f})
                .metadata(Map.of("doc_id", "1001"))
                .build();
    }
}
