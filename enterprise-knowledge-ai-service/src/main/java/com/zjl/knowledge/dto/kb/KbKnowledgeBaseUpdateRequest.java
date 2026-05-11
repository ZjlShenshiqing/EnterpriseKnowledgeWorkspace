package com.zjl.knowledge.dto.kb;

import lombok.Data;

/**
 * 更新知识库（不含重命名专用接口时可传展示名等）
 */
@Data
public class KbKnowledgeBaseUpdateRequest {

    private String name;

    private String embeddingModel;
}
