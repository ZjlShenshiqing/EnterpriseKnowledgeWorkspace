package com.zjl.knowledge.dto.chunk;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Chunk 对外视图
 */
@Data
public class KbChunkVO {

    private Long id;
    private Long documentId;
    private Integer chunkIndex;
    private String content;
    private String contentHash;
    private Integer charCount;
    private Integer tokenCount;
    private Integer enabled;
    private String vectorId;
    private String metadataJson;
    private Long createdBy;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
