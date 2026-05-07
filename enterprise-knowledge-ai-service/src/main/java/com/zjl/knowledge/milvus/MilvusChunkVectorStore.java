package com.zjl.knowledge.milvus;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link ChunkVectorStore} 的 Milvus 实现
 *
 * <p>将 {@code Long} 类型的 documentId 转为 String 后委托
 * {@link VectorStoreService}，适配业务层与底层向量存储之间的类型差异</p>
 */
@Component
@RequiredArgsConstructor
public class MilvusChunkVectorStore implements ChunkVectorStore {

    /**
     * 底层向量存储服务
     */
    private final VectorStoreService vectorStoreService;

    @Override
    public void deleteDocumentVectors(String collectionName, Long documentId) {
        vectorStoreService.deleteDocumentVectors(collectionName, String.valueOf(documentId));
    }

    @Override
    public void indexDocumentChunks(String collectionName, Long documentId, List<VectorDocChunk> chunks) {
        vectorStoreService.indexDocumentChunks(collectionName, String.valueOf(documentId), chunks);
    }

    @Override
    public void updateChunk(String collectionName, Long documentId, VectorDocChunk chunk) {
        vectorStoreService.updateChunk(collectionName, String.valueOf(documentId), chunk);
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        vectorStoreService.deleteChunkById(collectionName, chunkId);
    }

    @Override
    public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        vectorStoreService.deleteChunksByIds(collectionName, chunkIds);
    }
}
