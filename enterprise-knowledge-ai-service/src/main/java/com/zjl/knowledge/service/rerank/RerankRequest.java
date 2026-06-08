package com.zjl.knowledge.service.rerank;

import java.util.List;

/**
 * rerank 请求
 */
public record RerankRequest(
        /** 用户原始查询 */
        String query,
        /** 待排序的候选列表 */
        List<RerankedCandidate> candidates
) {}
