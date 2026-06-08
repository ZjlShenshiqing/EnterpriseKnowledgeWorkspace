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
     * 检索命中的单个 chunk，包含 rerank 调试字段
     */
    public record ChunkResult(
            int chunkIndex,
            String text,
            float score,
            Map<String, Object> metadata,
            /** rerank 得分，未启用时为原始分数 */
            float rerankScore,
            /** 使用的 rerank 策略 */
            String rerankStrategy,
            /** rerank 排序原因 */
            String rerankReason
    ) {
        /** 兼容构造：没有 rerank 元数据 */
        public ChunkResult(int chunkIndex, String text, float score, Map<String, Object> metadata) {
            this(chunkIndex, text, score, metadata, score, "NONE", "");
        }
    }
}
