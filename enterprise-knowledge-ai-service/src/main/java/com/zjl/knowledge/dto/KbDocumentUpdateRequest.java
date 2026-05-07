package com.zjl.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 更新文档元数据（分块运行中禁止修改）
 */
@Data
public class KbDocumentUpdateRequest {

    @NotBlank
    private String title;

    private String processMode;

    private String chunkStrategy;

    private String chunkConfig;

    private String pipelineId;

    private String sourceLocation;

    private Integer scheduleEnabled;

    private String scheduleCron;
}
