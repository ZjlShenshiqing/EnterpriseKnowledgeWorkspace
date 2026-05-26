package com.zjl.knowledge.event;

/**
 * 知识库更新事件 — 触发流水线配置同步。
 */
public record KnowledgeBaseUpdatedEvent(Long kbId) {
}
