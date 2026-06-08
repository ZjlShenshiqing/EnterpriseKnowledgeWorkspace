package com.zjl.knowledge.service;

import com.zjl.knowledge.dto.kb.HybridIndexRebuildResult;

/**
 * Hybrid 索引重建服务。
 */
public interface HybridIndexRebuildService {

    /**
     * 重建单个文档的 hybrid 索引。
     *
     * @param documentId 文档 ID
     * @return 重建结果
     */
    HybridIndexRebuildResult rebuildDocument(Long documentId);

    /**
     * 按批次重建历史 SUCCESS 文档的 hybrid 索引。
     *
     * @param limit 单次最多处理文档数
     * @return 重建结果
     */
    HybridIndexRebuildResult rebuildSuccessDocuments(int limit);
}
