package com.zjl.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * RAG 检索相关配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.rag.retrieval")
public class RagRetrievalProperties {

    /** 检索模式枚举 */
    public enum RetrievalMode {
        VECTOR_ONLY,
        HYBRID_MILVUS,
        KEYWORD_DB
    }

    /**
     * 检索模式，默认 VECTOR_ONLY
     */
    private RetrievalMode mode = RetrievalMode.HYBRID_MILVUS;

    /**
     * 每路 ANN 请求的 topK 倍数，用于扩大候选集
     */
    private int topNMultiplier = 5;

    /**
     * Ranker 配置
     */
    private Ranker ranker = new Ranker();

    /**
     * 最低分数过滤
     */
    private MinScore minScore = new MinScore();

    @Data
    public static class Ranker {
        /** Ranker 类型，当前仅支持 RRF */
        private String type = "RRF";

        /** RRF k 参数 */
        private int rrfK = 60;
    }

    @Data
    public static class MinScore {
        /** 是否启用最低分数过滤 */
        private boolean enabled = false;

        /** 最低分数阈值 */
        private double value = 0.01;
    }
}
