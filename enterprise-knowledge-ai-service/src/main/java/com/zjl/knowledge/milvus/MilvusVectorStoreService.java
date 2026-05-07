package com.zjl.knowledge.milvus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Milvus 向量存储实现（行为对齐参考 {@code MilvusVectorStoreService}，委托 {@link MilvusVectorWriter}）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusVectorStoreService implements VectorStoreService {

    private final MilvusVectorWriter milvusVectorWriter;

    @Override
    public void indexDocumentChunks(String collectionName, String docId, List<VectorDocChunk> chunks) {
        milvusVectorWriter.indexDocumentChunks(collectionName, docId, chunks);
    }

    @Override
    public void updateChunk(String collectionName, String docId, VectorDocChunk chunk) {
        milvusVectorWriter.upsertChunk(collectionName, docId, chunk);
    }

    @Override
    public void deleteDocumentVectors(String collectionName, String docId) {
        milvusVectorWriter.deleteByDocumentId(collectionName, docId);
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
