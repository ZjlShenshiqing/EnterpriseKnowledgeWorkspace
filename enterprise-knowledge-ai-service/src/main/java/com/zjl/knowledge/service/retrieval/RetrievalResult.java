package com.zjl.knowledge.service.retrieval;

import java.util.List;
import java.util.Map;

/**
 * RAG 检索结果：包含匹配文档列表
 */
public record RetrievalResult(List<DocumentResult> documents) {

    /**
     * 检索命中的单个文档及其匹配 chunk
     */
    public record DocumentResult(
            Long documentId,
            String title,
            String summary,
            String fileType,
            String fileName,
            Long fileSize,
            Object createdAt,
            Map<String, Object> metadata,
            List<ChunkResult> matchedChunks
    ) {}

    /**
     * 检索命中的单个 chunk
     */
    public record ChunkResult(
            int chunkIndex,
            String text,
            float score,
            Map<String, Object> metadata
    ) {}
}
