package com.zjl.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块任务表
 * 用于持久化分块任务，支持服务重启后恢复
 */
@Data
@TableName("kb_chunk_task")
public class KbChunkTask {

    /**
     * 主键 ID
     */
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    /**
     * 文档 ID
     */
    private Long documentId;

    /**
     * 操作用户 ID
     */
    private Long operatorUserId;

    /**
     * 任务状态：PENDING/RUNNING/SUCCESS/FAILED
     */
    private String status;

    /**
     * 重试次数
     */
    private Integer retryCount;

    /**
     * 最大重试次数
     */
    private Integer maxRetryCount;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 开始时间
     */
    private LocalDateTime startedAt;

    /**
     * 结束时间
     */
    private LocalDateTime endedAt;

    /**
     * 超时时间（分钟）
     */
    private Integer timeoutMinutes;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}