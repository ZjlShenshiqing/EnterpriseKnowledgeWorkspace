package com.zjl.knowledge.dto.kb;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建知识库请求
 */
@Data
public class KbKnowledgeBaseCreateRequest {

    @NotBlank
    private String name;

    /**
     * Milvus 集合名，需符合 Milvus 命名规范
     */
    @NotBlank
    private String collectionName;

    /**
     * 可选；为空时使用全局嵌入模型配置
     */
    private String embeddingModel;
}
