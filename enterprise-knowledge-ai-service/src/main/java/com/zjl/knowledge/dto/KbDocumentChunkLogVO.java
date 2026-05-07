package com.zjl.knowledge.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class KbDocumentChunkLogVO {

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
