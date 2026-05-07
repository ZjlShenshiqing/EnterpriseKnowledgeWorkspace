package com.zjl.knowledge.milvus;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Milvus 版 {@link ChunkVectorStore}，内部委托 {@link VectorStoreService}（与参考实现一致）。
 */
@Component
@RequiredArgsConstructor
public class MilvusChunkVectorStore implements ChunkVectorStore {

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
