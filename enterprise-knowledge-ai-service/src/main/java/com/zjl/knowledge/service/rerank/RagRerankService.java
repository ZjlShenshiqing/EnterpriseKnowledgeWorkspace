package com.zjl.knowledge.service.rerank;

import java.util.List;

/**
 * RAG rerank 服务：对召回候选进行二次排序
 */
public interface RagRerankService {

    /**
     * 对候选列表重排序
     *
     * @param request rerank 请求
     * @return 按 rerankScore 降序排列的候选列表
     */
    List<RerankedCandidate> rerank(RerankRequest request);
}
