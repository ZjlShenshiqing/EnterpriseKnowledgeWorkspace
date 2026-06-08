package com.zjl.knowledge.service.rerank;

/**
 * Rerank 策略枚举
 */
public enum RerankStrategy {

    /** 不执行 rerank，保持原始召回顺序 */
    NONE,

    /** 本地特征 rerank */
    LOCAL_FEATURE,

    /** 阿里云百炼 Qwen3 rerank */
    BAILIAN_QWEN3
}
