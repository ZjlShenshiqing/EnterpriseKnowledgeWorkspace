package com.zjl.knowledge.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 文档分块任务执行日志
 */
@Data
@TableName("kb_document_chunk_log")
public class KbDocumentChunkLog {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long documentId;

    private String status;

    private String processMode;

    private String chunkStrategy;

    private String pipelineId;

    private Integer chunkCount;

    private Long extractDurationMs;

    private Long chunkDurationMs;

    private Long embedDurationMs;

    private Long persistDurationMs;

    private Long totalDurationMs;

    private String errorMessage;

    private LocalDateTime startedAt;

    private LocalDateTime endedAt;
}
