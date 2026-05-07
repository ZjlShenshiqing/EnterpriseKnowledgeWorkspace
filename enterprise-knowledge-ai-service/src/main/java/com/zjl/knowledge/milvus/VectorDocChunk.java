package com.zjl.knowledge.milvus;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 写入向量库的一条切片载荷（对齐参考：id + content + metadata + embedding）。
 */
@Data
@Builder
public class VectorDocChunk {

    private String chunkId;
    private String content;
    private int index;
    private float[] embedding;

    /**
     * 可选；合并进 Milvus metadata JSON（与业务 metadata 扩展字段）。
     */
    private Map<String, Object> metadata;
}
