package com.zjl.knowledge.milvus;

import java.util.List;

/**
 * 向量存储底层抽象
 *
 * <p>方法语义与参考工程对齐，以 {@link VectorDocChunk} 作为切片载荷，
 * 当前落地为 {@link MilvusVectorStoreService}</p>
 */
public interface VectorStoreService {

    /**
     * 批量写入文档切片向量
     *
     * @param collectionName Milvus 集合名
     * @param docId          文档 ID 字符串
     * @param chunks         待写入的切片列表
     */
    void indexDocumentChunks(String collectionName, String docId, List<VectorDocChunk> chunks);

    /**
     * 更新单条切片向量（Upsert 语义）
     *
     * @param collectionName Milvus 集合名
     * @param docId          文档 ID 字符串
     * @param chunk          待更新的切片
     */
    void updateChunk(String collectionName, String docId, VectorDocChunk chunk);

    /**
     * 按文档 ID 删除该文档所有向量
     *
     * @param collectionName Milvus 集合名
     * @param docId          文档 ID 字符串
     */
    void deleteDocumentVectors(String collectionName, String docId);

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
