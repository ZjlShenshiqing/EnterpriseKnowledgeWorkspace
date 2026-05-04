package com.zjl.knowledge.event;

import com.zjl.knowledge.service.KbDocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 文档分块异步执行（替代 MQ 的最小实现）
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentChunkEventListener {

    private final KbDocumentService kbDocumentService;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onChunkRequested(DocumentChunkRequestedEvent event) {
        try {
            kbDocumentService.executeChunk(event.documentId(), event.operatorUserId());
        } catch (Exception ex) {
            log.error("异步分块执行失败, documentId={}", event.documentId(), ex);
        }
    }
}
