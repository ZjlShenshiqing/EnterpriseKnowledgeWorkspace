package com.zjl.knowledge.scheduler;

import com.zjl.knowledge.service.KbChunkTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 分块任务定时调度器
 * 负责扫描和恢复超时任务
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChunkTaskScheduler {

    private final KbChunkTaskService chunkTaskService;

    /**
     * 每 5 分钟扫描一次超时任务
     */
    @Scheduled(fixedRate = 5 * 60 * 1000)
    public void recoverTimeoutTasks() {
        try {
            int recovered = chunkTaskService.recoverTimeoutTasks();
            if (recovered > 0) {
                log.info("定时任务：恢复了 {} 个超时任务", recovered);
            }
        } catch (Exception e) {
            log.error("定时任务：恢复超时任务失败", e);
        }
    }
}