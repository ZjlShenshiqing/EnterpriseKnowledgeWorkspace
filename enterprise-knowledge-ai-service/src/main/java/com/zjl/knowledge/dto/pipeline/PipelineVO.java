package com.zjl.knowledge.dto.pipeline;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 流水线定义视图。
 */
@Data
public class PipelineVO {

    private Long id;
    private Long knowledgeBaseId;
    private String name;
    private String description;
    private List<String> stages;
    private String chunkStrategy;
    private Boolean vectorEnabled;
    private String embeddingModel;
    private String status;
    private LocalDateTime updatedAt;
}
