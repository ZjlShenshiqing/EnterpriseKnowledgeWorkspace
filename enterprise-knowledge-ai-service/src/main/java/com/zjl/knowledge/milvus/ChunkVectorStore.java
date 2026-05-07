package com.zjl.knowledge.milvus;

import java.util.List;

/**
 * 向量库存取抽象门面
 *
 * <p>业务层通过此接口操作向量，不直接依赖具体实现，
 * 当前落地为 {@link MilvusChunkVectorStore}</p>
 */
public interface ChunkVectorStore {

    /**
     * 按文档 ID 删除该文档所有向量
     *
     * @param collectionName Milvus 集合名，{@code null} 或空串时使用默认集合
     * @param documentId     文档 ID
     */
    void deleteDocumentVectors(String collectionName, Long documentId);

    /**
     * 批量写入文档的切片向量
     *
     * @param collectionName Milvus 集合名
     * @param documentId     文档 ID
     * @param chunks         待写入的切片列表（含向量）
     */
    void indexDocumentChunks(String collectionName, Long documentId, List<VectorDocChunk> chunks);

    /**
     * 更新单条切片向量（Upsert）
     *
     * @param collectionName Milvus 集合名
     * @param documentId     文档 ID
     * @param chunk          待更新的切片（含新的 content 和 embedding）
     */
    void updateChunk(String collectionName, Long documentId, VectorDocChunk chunk);

    /**
     * 按切片主键删除单条向量
     *
     * @param collectionName Milvus 集合名
     * @param chunkId        切片主键字符串
     */
    void deleteChunkById(String collectionName, String chunkId);

    /**
     * 按切片主键列表批量删除向量
     *
     * @param collectionName Milvus 集合名
     * @param chunkIds       切片主键字符串列表
     */
    void deleteChunksByIds(String collectionName, List<String> chunkIds);
}
