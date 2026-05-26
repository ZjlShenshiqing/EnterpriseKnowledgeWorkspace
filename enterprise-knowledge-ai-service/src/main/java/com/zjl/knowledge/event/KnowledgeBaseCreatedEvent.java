package com.zjl.knowledge.event;

/**
 * 知识库创建事件 — 触发流水线自动创建。
 */
public record KnowledgeBaseCreatedEvent(Long kbId) {
}
