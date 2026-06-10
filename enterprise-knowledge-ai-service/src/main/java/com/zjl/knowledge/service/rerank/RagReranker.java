package com.zjl.knowledge.service.rerank;

import com.zjl.framework.starter.designpattern.staregy.AbstractExecuteStrategy;

import java.util.List;

/**
 * Rerank 策略接口，基于设计模式模块的策略模式自动注册
 */
public interface RagReranker extends AbstractExecuteStrategy<RerankRequest, List<RerankedCandidate>> {

    @Override
    default String mark() {
        return strategy().name();
    }

    /**
     * 返回对应的 rerank 策略枚举
     */
    RerankStrategy strategy();

    /**
     * 执行 rerank 排序
     */
    @Override
    List<RerankedCandidate> executeResp(RerankRequest request);
}
