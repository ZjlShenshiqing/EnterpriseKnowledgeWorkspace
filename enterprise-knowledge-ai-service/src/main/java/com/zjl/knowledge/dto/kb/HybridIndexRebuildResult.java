package com.zjl.knowledge.dto.kb;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Hybrid 索引重建结果。
 */
@Data
@Builder
public class HybridIndexRebuildResult {

    /**
     * 扫描文档数。
     */
    private int total;

    /**
     * 成功重建文档数。
     */
    private int success;

    /**
     * 跳过文档数。
     */
    private int skipped;

    /**
     * 失败文档数。
     */
    private int failed;

    /**
     * 成功写入的切片数。
     */
    private int chunkCount;

    /**
     * 失败文档 ID。
     */
    private List<Long> failedDocumentIds;

    /**
     * 跳过文档 ID。
     */
    private List<Long> skippedDocumentIds;
}
