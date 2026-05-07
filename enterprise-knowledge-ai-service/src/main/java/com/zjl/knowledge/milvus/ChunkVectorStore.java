package com.zjl.knowledge.milvus;

import java.util.List;

/**
 * 向量库存取抽象（当前实现为 Milvus 单集合）。
 */
public interface ChunkVectorStore {

    /**
     * @param collectionName Milvus 集合名；{@code null} 或空串时使用 {@code app.milvus.collection}
     */
    void deleteDocumentVectors(String collectionName, Long documentId);

    void indexDocumentChunks(String collectionName, Long documentId, List<VectorDocChunk> chunks);

    void updateChunk(String collectionName, Long documentId, VectorDocChunk chunk);

    void deleteChunkById(String collectionName, String chunkId);

    void deleteChunksByIds(String collectionName, List<String> chunkIds);
}
