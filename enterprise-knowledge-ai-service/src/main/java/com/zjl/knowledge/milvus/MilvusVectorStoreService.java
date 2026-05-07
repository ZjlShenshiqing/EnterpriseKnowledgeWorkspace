package com.zjl.knowledge.milvus;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * {@link VectorStoreService} 的 Milvus 实现
 *
 * <p>纯委托层，将接口调用转发给 {@link MilvusVectorWriter}，
 * 方法语义与底层写入器一一对应</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MilvusVectorStoreService implements VectorStoreService {

    /**
     * Milvus 底层写入器
     */
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
