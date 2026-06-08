package com.zjl.knowledge.service.rerank;

import java.util.Map;

/**
 * rerank 候选片段，保留原始召回字段并补充 rerank 元数据
 */
public record RerankedCandidate(
        /** 文档 ID */
        Long documentId,
        /** chunk ID */
        Long chunkId,
        /** chunk 在文档中的序号 */
        int chunkIndex,
        /** chunk 文本 */
        String text,
        /** 原始召回分数 */
        float originalScore,
        /** 原始召回排名（从 1 开始） */
        int originalRank,
        /** 召回来源 */
        String retrievalSource,
        /** chunk metadata（含标题、章节路径等） */
        Map<String, Object> metadata,
        /** rerank 得分，关闭 rerank 时为原分数 */
        float rerankScore,
        /** rerank 策略 */
        String rerankStrategy,
        /** rerank 排序原因 */
        String rerankReason
) {}
