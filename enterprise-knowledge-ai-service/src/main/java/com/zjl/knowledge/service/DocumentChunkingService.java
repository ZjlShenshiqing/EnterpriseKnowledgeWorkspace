package com.zjl.knowledge.service;

import com.zjl.common.context.UserContext;

/**
 * 文档分块服务
 */
public interface DocumentChunkingService {

    void startChunk(Long documentId, UserContext user);

    void executeChunk(Long documentId, Long operatorUserId);

    void executeChunkAsUser(Long documentId, UserContext user);
}
