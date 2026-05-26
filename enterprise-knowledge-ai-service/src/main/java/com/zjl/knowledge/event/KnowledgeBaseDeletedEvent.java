package com.zjl.knowledge.event;

/**
 * 知识库删除事件 — 触发流水线软删除。
 */
public record KnowledgeBaseDeletedEvent(Long kbId) {
}
