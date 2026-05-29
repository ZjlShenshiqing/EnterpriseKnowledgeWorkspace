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

    void deleteDocumentVectors(KbDocument document);

    void deleteChunkVector(KbDocument document, String chunkId);

    void deleteChunkVectors(KbDocument document, List<String> chunkIds);

    List<com.zjl.knowledge.milvus.SearchResult> searchSimilar(String query, int topK, KbDocument document);
}
