package com.zjl.knowledge.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.entity.KbChunkTask;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.mapper.KbChunkTaskMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分块任务管理服务
 * 提供任务持久化、状态管理、超时恢复等功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KbChunkTaskService {

    private final KbChunkTaskMapper taskMapper;
    private final KbDocumentMapper documentMapper;

    /**
     * 创建分块任务
     */
    @Transactional(rollbackFor = Exception.class)
    public Long createTask(Long documentId, Long operatorUserId, int timeoutMinutes) {
        // 检查是否已存在未完成的任务
        KbChunkTask existing = taskMapper.selectOne(Wrappers.lambdaQuery(KbChunkTask.class)
                .eq(KbChunkTask::getDocumentId, documentId)
                .in(KbChunkTask::getStatus, "PENDING", "RUNNING")
                .last("LIMIT 1"));
        
        if (existing != null) {
            log.warn("文档已存在未完成的分块任务: documentId={}, taskId={}", documentId, existing.getId());
            return existing.getId();
        }

        KbChunkTask task = new KbChunkTask();
        task.setDocumentId(documentId);
        task.setOperatorUserId(operatorUserId);
        task.setStatus("PENDING");
        task.setRetryCount(0);
        task.setMaxRetryCount(3);
        task.setTimeoutMinutes(timeoutMinutes);
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        
        taskMapper.insert(task);
        log.info("创建分块任务: taskId={}, documentId={}, operatorUserId={}", task.getId(), documentId, operatorUserId);
        
        return task.getId();
    }

    /**
     * 标记任务开始执行
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean markTaskRunning(Long taskId) {
        int rows = taskMapper.update(null, Wrappers.lambdaUpdate(KbChunkTask.class)
                .eq(KbChunkTask::getId, taskId)
                .eq(KbChunkTask::getStatus, "PENDING")
                .set(KbChunkTask::getStatus, "RUNNING")
                .set(KbChunkTask::getStartedAt, LocalDateTime.now())
                .set(KbChunkTask::getUpdatedAt, LocalDateTime.now()));
        
        if (rows > 0) {
            log.info("任务开始执行: taskId={}", taskId);
        } else {
            log.warn("任务状态已变更，无法标记为 RUNNING: taskId={}", taskId);
        }
        return rows > 0;
    }

    /**
     * 标记任务成功
     */
    @Transactional(rollbackFor = Exception.class)
    public void markTaskSuccess(Long taskId) {
        taskMapper.update(null, Wrappers.lambdaUpdate(KbChunkTask.class)
                .eq(KbChunkTask::getId, taskId)
                .set(KbChunkTask::getStatus, "SUCCESS")
                .set(KbChunkTask::getEndedAt, LocalDateTime.now())
                .set(KbChunkTask::getUpdatedAt, LocalDateTime.now()));
        log.info("任务执行成功: taskId={}", taskId);
    }

    /**
     * 标记任务失败
     */
    @Transactional(rollbackFor = Exception.class)
    public void markTaskFailed(Long taskId, String errorMessage) {
        KbChunkTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }

        int newRetryCount = task.getRetryCount() + 1;
        String newStatus = newRetryCount >= task.getMaxRetryCount() ? "FAILED" : "PENDING";

        taskMapper.update(null, Wrappers.lambdaUpdate(KbChunkTask.class)
                .eq(KbChunkTask::getId, taskId)
                .set(KbChunkTask::getStatus, newStatus)
                .set(KbChunkTask::getRetryCount, newRetryCount)
                .set(KbChunkTask::getErrorMessage, errorMessage)
                .set(KbChunkTask::getEndedAt, LocalDateTime.now())
                .set(KbChunkTask::getUpdatedAt, LocalDateTime.now()));

        log.info("任务执行失败: taskId={}, status={}, retryCount={}/{}, error={}", 
                taskId, newStatus, newRetryCount, task.getMaxRetryCount(), errorMessage);
    }

    /**
     * 扫描并恢复超时任务
     */
    @Transactional(rollbackFor = Exception.class)
    public int recoverTimeoutTasks() {
        LocalDateTime timeoutThreshold = LocalDateTime.now().minusMinutes(10); // 默认 10 分钟超时

        List<KbChunkTask> timeoutTasks = taskMapper.selectList(Wrappers.lambdaQuery(KbChunkTask.class)
                .eq(KbChunkTask::getStatus, "RUNNING")
                .lt(KbChunkTask::getStartedAt, timeoutThreshold));

        if (timeoutTasks.isEmpty()) {
            return 0;
        }

        log.info("发现 {} 个超时任务，开始恢复", timeoutTasks.size());

        int recovered = 0;
        for (KbChunkTask task : timeoutTasks) {
            try {
                // 检查文档状态，如果已经是 SUCCESS，则标记任务成功
                KbDocument doc = documentMapper.selectById(task.getDocumentId());
                if (doc != null && DocumentStatus.SUCCESS.name().equals(doc.getStatus())) {
                    markTaskSuccess(task.getId());
                    recovered++;
                    continue;
                }

                // 重置任务状态为 PENDING，允许重新执行
                int newRetryCount = task.getRetryCount() + 1;
                if (newRetryCount >= task.getMaxRetryCount()) {
                    markTaskFailed(task.getId(), "任务超时且达到最大重试次数");
                    // 同时标记文档为 FAILED
                    documentMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                            .eq(KbDocument::getId, task.getDocumentId())
                            .set(KbDocument::getStatus, DocumentStatus.FAILED.name())
                            .set(KbDocument::getSummary, "任务超时且达到最大重试次数")
                            .set(KbDocument::getUpdatedAt, LocalDateTime.now()));
                } else {
                    taskMapper.update(null, Wrappers.lambdaUpdate(KbChunkTask.class)
                            .eq(KbChunkTask::getId, task.getId())
                            .set(KbChunkTask::getStatus, "PENDING")
                            .set(KbChunkTask::getRetryCount, newRetryCount)
                            .set(KbChunkTask::getErrorMessage, "任务超时，重置为 PENDING")
                            .set(KbChunkTask::setUpdatedAt, LocalDateTime.now()));
                    
                    // 重置文档状态为 FAILED，允许重新提交
                    documentMapper.update(null, Wrappers.lambdaUpdate(KbDocument.class)
                            .eq(KbDocument::getId, task.getDocumentId())
                            .set(KbDocument::getStatus, DocumentStatus.FAILED.name())
                            .set(KbDocument::getSummary, "任务超时，已重置为 FAILED 可重新提交")
                            .set(KbDocument::getUpdatedAt, LocalDateTime.now()));
                    
                    recovered++;
                }
            } catch (Exception e) {
                log.error("恢复超时任务失败: taskId={}", task.getId(), e);
            }
        }

        log.info("超时任务恢复完成: recovered={}/{}", recovered, timeoutTasks.size());
        return recovered;
    }

    /**
     * 获取待执行的任务
     */
    public List<KbChunkTask> getPendingTasks(int limit) {
        return taskMapper.selectList(Wrappers.lambdaQuery(KbChunkTask.class)
                .eq(KbChunkTask::getStatus, "PENDING")
                .orderByAsc(KbChunkTask::getCreatedAt)
                .last("LIMIT " + limit));
    }
}