package com.zjl.knowledge.event;

/**
 * 请求异步执行文档分块（事务提交后由监听器触发）。
 */
public record DocumentChunkRequestedEvent(Long documentId, Long operatorUserId) {
}
