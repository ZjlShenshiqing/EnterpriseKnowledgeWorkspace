package com.zjl.knowledge.event;

import com.zjl.knowledge.service.PipelineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 监听知识库生命周期事件，同步维护流水线记录。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PipelineEventListener {

    private final PipelineService pipelineService;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onKbCreated(KnowledgeBaseCreatedEvent event) {
        try {
            pipelineService.createPipelineForKb(event.kbId());
        } catch (Exception e) {
            log.error("自动创建流水线失败, kbId={}", event.kbId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onKbUpdated(KnowledgeBaseUpdatedEvent event) {
        try {
            pipelineService.syncPipelineFromKb(event.kbId());
        } catch (Exception e) {
            log.error("同步流水线配置失败, kbId={}", event.kbId(), e);
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onKbDeleted(KnowledgeBaseDeletedEvent event) {
        try {
            pipelineService.deletePipelineByKbId(event.kbId());
        } catch (Exception e) {
            log.error("删除流水线失败, kbId={}", event.kbId(), e);
        }
    }
}
