package com.zjl.knowledge.dto.pipeline;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 流水线任务视图 — 映射 kb_document_chunk_log 记录。
 */
@Data
public class PipelineTaskVO {

    private String taskId;
    private String type;
    private String documentName;
    private Long pipelineId;
    private String pipelineName;
    private String progress;
    private String status;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
