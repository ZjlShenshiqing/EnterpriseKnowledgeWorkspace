package com.zjl.knowledge.service.rerank;

import java.util.List;

/**
 * Rerank 策略接口，支持本地策略和未来模型策略
 */
public interface RagReranker {

    /**
     * 是否支持指定策略
     */
    boolean supports(RerankStrategy strategy);

    /**
     * 执行 rerank 排序
     *
     * @param request rerank 请求
     * @return 按 rerankScore 降序排列的候选列表
     */
    List<RerankedCandidate> rerank(RerankRequest request);
}
