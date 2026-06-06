package com.zjl.knowledge.milvus;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 写入向量库的一条切片载荷
 *
 * <p>VECTOR_ONLY 模式使用：chunkId→id、content→content、
 * index+metadata→metadata(JSON)、embedding→embedding</p>
 * <p>HYBRID 模式额外需要 sparseVector</p>
 */
@Data
@Builder
public class VectorDocChunk {

    /**
     * 切片主键，与 Milvus 行主键 {@code id} 及 {@code kb_document_chunk.id} 一一对应
     */
    private String chunkId;

    /**
     * 切片正文（超长时由写入方截断至 65535 字符）
     */
    private String content;

    /**
     * 切片在原文档中的序号，从 0 递增
     */
    private int index;

    /**
     * FloatVector 向量数组，维度需与集合 Schema 一致
     */
    private float[] embedding;

    /**
     * 稀疏向量（仅在 HYBRID 模式下使用），term 位置 → 权重
     */
    private Map<Long, Float> sparseVector;

    /**
     * 可选扩展元数据，写入时会合并到 Milvus metadata JSON 中
     */
    private Map<String, Object> metadata;
}
