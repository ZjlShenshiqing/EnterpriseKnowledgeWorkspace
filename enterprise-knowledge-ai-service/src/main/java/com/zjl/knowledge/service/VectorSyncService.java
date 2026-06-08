package com.zjl.knowledge.service;

import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.milvus.VectorDocChunk;
import java.util.List;

/**
 * 向量同步服务
 */
public interface VectorSyncService {

    List<Float> embed(String text, KbDocument document);

    List<List<Float>> embedBatch(List<String> texts, KbDocument document);

    boolean shouldEmbed(KbDocument document);

    String resolveCollection(KbDocument document);

    String resolveCollectionOrDefault(KbDocument document);

    void syncChunk(KbDocument document, KbDocumentChunk chunk);

    void syncChunks(KbDocument document, List<KbDocumentChunk> chunks);

    void updateChunk(KbDocument document, KbDocumentChunk chunk);

    void indexDocumentChunks(KbDocument document, List<VectorDocChunk> vectorChunks);

    void rebuildHybridChunks(KbDocument document, List<KbDocumentChunk> chunks);

    void deleteDocumentVectors(KbDocument document);

    void deleteChunkVector(KbDocument document, String chunkId);

    void deleteChunkVectors(KbDocument document, List<String> chunkIds);

    List<com.zjl.knowledge.milvus.SearchResult> searchSimilar(String query, int topK, KbDocument document);

    /**
     * Hybrid 检索：同时生成 dense 和 sparse 向量，执行双路检索 + RRF 融合
     *
     * @param query    用户查询文本
     * @param topK     最终返回数量
     * @param document 知识库文档（用于路由和 kbId 过滤，kbId 为 {@code null} 时检索所有知识库）
     * @return 融合后的搜索结果
     */
    List<com.zjl.knowledge.milvus.SearchResult> hybridSearchSimilar(String query, int topK, KbDocument document);
}
