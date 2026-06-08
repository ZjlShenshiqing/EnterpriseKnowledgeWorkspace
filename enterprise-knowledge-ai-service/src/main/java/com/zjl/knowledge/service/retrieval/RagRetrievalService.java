package com.zjl.knowledge.service.retrieval;

import com.zjl.knowledge.web.UserContext;

/**
 * 统一 RAG 检索服务入口
 */
public interface RagRetrievalService {

    /**
     * 执行 RAG 检索：模式选择 → 检索 → DB查询 → 权限终检 → 结果组装
     *
     * @param question 用户查询文本
     * @param topK     最大返回文档数
     * @param user     当前用户上下文
     * @param kbId     知识库 ID，为 {@code null} 时检索所有知识库
     * @return 检索结果
     */
    RetrievalResult retrieve(String question, int topK, UserContext user, Long kbId);
}
