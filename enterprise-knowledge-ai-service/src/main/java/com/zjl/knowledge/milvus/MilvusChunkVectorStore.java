package com.zjl.knowledge.milvus;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * {@link ChunkVectorStore} 的 Milvus 实现，直接委托 {@link MilvusVectorWriter}。
 */
@Component
@RequiredArgsConstructor
public class MilvusChunkVectorStore implements ChunkVectorStore {

    private final MilvusVectorWriter milvusVectorWriter;

    @Override
    public void deleteDocumentVectors(String collectionName, Long documentId) {
        milvusVectorWriter.deleteByDocumentId(collectionName, String.valueOf(documentId));
    }

    @Override
    public void indexDocumentChunks(String collectionName, Long documentId, List<VectorDocChunk> chunks) {
        milvusVectorWriter.indexDocumentChunks(collectionName, String.valueOf(documentId), chunks);
    }

    @Override
    public void updateChunk(String collectionName, Long documentId, VectorDocChunk chunk) {
        milvusVectorWriter.upsertChunk(collectionName, String.valueOf(documentId), chunk);
    }

    @Override
    public void deleteChunkById(String collectionName, String chunkId) {
        milvusVectorWriter.deleteByChunkId(collectionName, chunkId);
    }

    @Override
    public void deleteChunksByIds(String collectionName, List<String> chunkIds) {
        milvusVectorWriter.deleteByChunkIds(collectionName, chunkIds);
    }
}
