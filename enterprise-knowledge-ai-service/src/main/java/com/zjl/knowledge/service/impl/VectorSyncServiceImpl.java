package com.zjl.knowledge.service.impl;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.config.MilvusProperties;
import com.zjl.knowledge.config.RagRetrievalProperties;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.embedding.EmbeddingService;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.metadata.ChunkMetadata;
import com.zjl.knowledge.milvus.ChunkVectorStore;
import com.zjl.knowledge.milvus.MilvusVectorWriter;
import com.zjl.knowledge.milvus.SearchResult;
import com.zjl.knowledge.milvus.SparseVectorGenerator;
import com.zjl.knowledge.milvus.VectorDocChunk;
import com.zjl.knowledge.service.KbMilvusRoutingService;
import com.zjl.knowledge.service.VectorSyncService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class VectorSyncServiceImpl implements VectorSyncService {

    private final EmbeddingService embeddingService;
    private final ChunkVectorStore chunkVectorStore;
    private final KbMilvusRoutingService kbMilvusRoutingService;
    private final SparseVectorGenerator sparseVectorGenerator;
    private final MilvusVectorWriter milvusVectorWriter;
    private final RagRetrievalProperties retrievalProperties;
    private final MilvusProperties milvusProperties;

    @Override
    public List<Float> embed(String text, KbDocument document) {
        String model = kbMilvusRoutingService.embeddingModelOrDefault(document);
        return StringUtils.hasText(model)
                ? embeddingService.embed(text, model)
                : embeddingService.embed(text);
    }

    @Override
    public List<List<Float>> embedBatch(List<String> texts, KbDocument document) {
        String model = kbMilvusRoutingService.embeddingModelOrDefault(document);
        return StringUtils.hasText(model)
                ? embeddingService.embedBatch(texts, model)
                : embeddingService.embedBatch(texts);
    }

    @Override
    public boolean shouldEmbed(KbDocument document) {
        return kbMilvusRoutingService.shouldEmbed(document);
    }

    @Override
    public String resolveCollection(KbDocument document) {
        return kbMilvusRoutingService.collectionForVectorWrite(document);
    }

    @Override
    public String resolveCollectionOrDefault(KbDocument document) {
        return kbMilvusRoutingService.collectionForVectorWriteOrDefault(document);
    }

    @Override
    public void syncChunk(KbDocument document, KbDocumentChunk chunk) {
        float[] vector = toArray(embed(chunk.getChunkText(), document));
        Map<String, Object> metaMap = buildMetaMap(chunk.getMetadataJson());
        if (document != null && document.getKbId() != null) {
            metaMap.put("kb_id", document.getKbId());
        }
        VectorDocChunk vc = VectorDocChunk.builder()
                .chunkId(String.valueOf(chunk.getId()))
                .content(chunk.getChunkText())
                .index(chunk.getChunkIndex())
                .metadata(metaMap)
                .embedding(vector)
                .build();
        String collection = resolveCollection(document);
        chunkVectorStore.indexDocumentChunks(collection, document.getId(), List.of(vc));
        if (isHybridMode()) {
            milvusVectorWriter.indexHybridChunks(
                    milvusProperties.getHybridCollection(),
                    String.valueOf(document.getId()),
                    List.of(withSparseVector(document, vc))
            );
        }
    }

    @Override
    public void syncChunks(KbDocument document, List<KbDocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        List<VectorDocChunk> vectorChunks = buildVectorChunks(document, chunks);
        String collection = resolveCollection(document);
        chunkVectorStore.indexDocumentChunks(collection, document.getId(), vectorChunks);
        if (isHybridMode()) {
            milvusVectorWriter.indexHybridChunks(
                    milvusProperties.getHybridCollection(),
                    String.valueOf(document.getId()),
                    withSparseVectors(document, vectorChunks)
            );
        }
    }

    @Override
    public void updateChunk(KbDocument document, KbDocumentChunk chunk) {
        float[] vector = toArray(embed(chunk.getChunkText(), document));
        Map<String, Object> metaMap = buildMetaMap(chunk.getMetadataJson());
        if (document != null && document.getKbId() != null) {
            metaMap.put("kb_id", document.getKbId());
        }
        String collection = resolveCollection(document);
        VectorDocChunk vectorChunk = VectorDocChunk.builder()
                .chunkId(String.valueOf(chunk.getId()))
                .content(chunk.getChunkText())
                .index(chunk.getChunkIndex())
                .metadata(metaMap)
                .embedding(vector)
                .build();
        chunkVectorStore.updateChunk(collection, document.getId(), vectorChunk);
        if (isHybridMode()) {
            milvusVectorWriter.upsertHybridChunk(
                    milvusProperties.getHybridCollection(),
                    String.valueOf(document.getId()),
                    withSparseVector(document, vectorChunk)
            );
        }
    }

    @Override
    public void indexDocumentChunks(KbDocument document, List<VectorDocChunk> vectorChunks) {
        if (vectorChunks == null || vectorChunks.isEmpty()) {
            return;
        }
        String collection = resolveCollection(document);
        chunkVectorStore.indexDocumentChunks(collection, document.getId(), vectorChunks);
        if (isHybridMode()) {
            milvusVectorWriter.indexHybridChunks(
                    milvusProperties.getHybridCollection(),
                    String.valueOf(document.getId()),
                    withSparseVectors(document, vectorChunks)
            );
        }
    }

    @Override
    public void rebuildHybridChunks(KbDocument document, List<KbDocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }
        String docId = String.valueOf(document.getId());
        List<VectorDocChunk> vectorChunks = buildVectorChunks(document, chunks);
        milvusVectorWriter.deleteByDocumentId(milvusProperties.getHybridCollection(), docId);
        milvusVectorWriter.indexHybridChunks(
                milvusProperties.getHybridCollection(),
                docId,
                withSparseVectors(document, vectorChunks)
        );
    }

    @Override
    public void deleteDocumentVectors(KbDocument document) {
        String collection = resolveCollectionOrDefault(document);
        chunkVectorStore.deleteDocumentVectors(collection, document.getId());
        if (isHybridMode()) {
            milvusVectorWriter.deleteByDocumentId(
                    milvusProperties.getHybridCollection(),
                    String.valueOf(document.getId())
            );
        }
    }

    @Override
    public void deleteChunkVector(KbDocument document, String chunkId) {
        String collection = resolveCollection(document);
        chunkVectorStore.deleteChunkById(collection, chunkId);
        if (isHybridMode()) {
            milvusVectorWriter.deleteByChunkId(milvusProperties.getHybridCollection(), chunkId);
        }
    }

    @Override
    public void deleteChunkVectors(KbDocument document, List<String> chunkIds) {
        String collection = resolveCollection(document);
        chunkVectorStore.deleteChunksByIds(collection, chunkIds);
        if (isHybridMode()) {
            milvusVectorWriter.deleteByChunkIds(milvusProperties.getHybridCollection(), chunkIds);
        }
    }

    @Override
    public List<SearchResult> searchSimilar(String query, int topK, KbDocument document) {
        RagRetrievalProperties.RetrievalMode mode = retrievalProperties.getMode();
        if (mode == RagRetrievalProperties.RetrievalMode.HYBRID_MILVUS) {
            try {
                return hybridSearchSimilar(query, topK, document);
            } catch (Exception ex) {
                log.warn("Hybrid search failed, falling back to VECTOR_ONLY. Error: {}", ex.getMessage());
                return vectorOnlySearch(query, topK, document);
            }
        }
        return vectorOnlySearch(query, topK, document);
    }

    @Override
    public List<SearchResult> hybridSearchSimilar(String query, int topK, KbDocument document) {
        float[] denseVec = toArray(embed(query, document));
        Map<Long, Float> sparseVec = sparseVectorGenerator.generateQuery(query);
        String collection = milvusProperties.getHybridCollection();
        RagRetrievalProperties.Ranker ranker = retrievalProperties.getRanker();
        int multiplier = retrievalProperties.getTopNMultiplier();
        Long kbId = document != null ? document.getKbId() : null;
        List<SearchResult> results = milvusVectorWriter.hybridSearch(
                collection, denseVec, sparseVec, topK, buildCoarseFilter(kbId), ranker.getRrfK(), multiplier);
        if (retrievalProperties.getMinScore().isEnabled()) {
            double threshold = retrievalProperties.getMinScore().getValue();
            results = results.stream()
                    .filter(r -> r.score() >= threshold)
                    .collect(Collectors.toList());
        }
        return results;
    }

    private List<SearchResult> vectorOnlySearch(String query, int topK, KbDocument document) {
        float[] vector = toArray(embed(query, document));
        String collection = resolveCollectionOrDefault(document);
        Long kbId = document != null ? document.getKbId() : null;
        String filter = buildKbIdFilter(kbId);
        return chunkVectorStore.search(collection, vector, topK, filter);
    }

    /**
     * 为 kbId 构建 Milvus 标量过滤表达式，null 时返回 null（不过滤）
     */
    private static String buildKbIdFilter(Long kbId) {
        if (kbId == null) {
            return null;
        }
        return "metadata[\"kb_id\"] == " + kbId;
    }

    private float[] toArray(List<Float> list) {
        float[] arr = new float[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }

    private List<VectorDocChunk> buildVectorChunks(KbDocument document, List<KbDocumentChunk> chunks) {
        List<String> texts = chunks.stream().map(KbDocumentChunk::getChunkText).collect(Collectors.toList());
        List<List<Float>> vectors = embedBatch(texts, document);
        if (vectors.size() != chunks.size()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, "向量结果数量与 Chunk 数不一致");
        }
        List<VectorDocChunk> result = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            KbDocumentChunk c = chunks.get(i);
            ChunkMetadata meta = ChunkMetadata.fromJson(c.getMetadataJson());
            Map<String, Object> metaMap = meta != null ? meta.toMap() : new HashMap<>();
            metaMap.put("document_status", document.getStatus());
            metaMap.put("document_enabled", document.getEnabled() == null || document.getEnabled() == 1);
            metaMap.put("chunk_enabled", c.getEnabled() == null || c.getEnabled() == 1);
            metaMap.put("kb_id", document.getKbId());
            result.add(VectorDocChunk.builder()
                    .chunkId(String.valueOf(c.getId()))
                    .content(c.getChunkText())
                    .index(c.getChunkIndex())
                    .metadata(metaMap)
                    .embedding(toArray(vectors.get(i)))
                    .build());
        }
        return result;
    }

    private boolean isHybridMode() {
        return retrievalProperties.getMode() == RagRetrievalProperties.RetrievalMode.HYBRID_MILVUS;
    }

    private List<VectorDocChunk> withSparseVectors(KbDocument document, List<VectorDocChunk> chunks) {
        return chunks.stream()
                .map(chunk -> withSparseVector(document, chunk))
                .collect(Collectors.toList());
    }

    private VectorDocChunk withSparseVector(KbDocument document, VectorDocChunk chunk) {
        return VectorDocChunk.builder()
                .chunkId(chunk.getChunkId())
                .content(chunk.getContent())
                .index(chunk.getIndex())
                .embedding(chunk.getEmbedding())
                .sparseVector(sparseVectorGenerator.generateDocument(chunk.getContent()))
                .metadata(withHybridFilterMetadata(document, chunk.getMetadata()))
                .build();
    }

    private Map<String, Object> withHybridFilterMetadata(KbDocument document, Map<String, Object> metadata) {
        Map<String, Object> result = new HashMap<>();
        if (metadata != null) {
            result.putAll(metadata);
        }
        result.putIfAbsent("document_status", effectiveDocumentStatus(document));
        result.putIfAbsent("document_enabled", document == null || document.getEnabled() == null || document.getEnabled() == 1);
        result.putIfAbsent("chunk_enabled", true);
        if (document != null && document.getKbId() != null) {
            result.putIfAbsent("kb_id", document.getKbId());
        }
        return result;
    }

    private String effectiveDocumentStatus(KbDocument document) {
        if (document == null || !StringUtils.hasText(document.getStatus())
                || DocumentStatus.RUNNING.name().equals(document.getStatus())) {
            return DocumentStatus.SUCCESS.name();
        }
        return document.getStatus();
    }

    /**
     * 构建 Milvus 粗过滤表达式，排除已禁用/已删除/未完成的文档和 chunk
     *
     * @param kbId 知识库 ID，为 {@code null} 时不限制知识库范围
     */
    private static String buildCoarseFilter(Long kbId) {
        String filter = "metadata[\"document_status\"] == \"SUCCESS\""
                + " && metadata[\"document_enabled\"] == true"
                + " && metadata[\"chunk_enabled\"] == true";
        if (kbId != null) {
            filter += " && metadata[\"kb_id\"] == " + kbId;
        }
        return filter;
    }

    private Map<String, Object> buildMetaMap(String metadataJson) {
        ChunkMetadata meta = ChunkMetadata.fromJson(metadataJson);
        return meta != null ? meta.toMap() : Collections.emptyMap();
    }
}
