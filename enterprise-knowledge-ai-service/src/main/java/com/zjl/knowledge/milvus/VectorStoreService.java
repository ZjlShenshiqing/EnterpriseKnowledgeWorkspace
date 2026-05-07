package com.zjl.knowledge.milvus;

import java.util.List;

/**
 * 向量存储抽象（与参考 {@code VectorStoreService} 方法语义对齐；本项目使用 {@link VectorDocChunk} 作为切片载荷）。
 */
public interface VectorStoreService {

    void indexDocumentChunks(String collectionName, String docId, List<VectorDocChunk> chunks);

    void updateChunk(String collectionName, String docId, VectorDocChunk chunk);

    void deleteDocumentVectors(String collectionName, String docId);

    void deleteChunkById(String collectionName, String chunkId);

    void deleteChunksByIds(String collectionName, List<String> chunkIds);
}
