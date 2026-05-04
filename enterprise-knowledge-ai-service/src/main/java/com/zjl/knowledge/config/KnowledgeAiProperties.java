package com.zjl.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 知识库业务相关配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "app.knowledge")
public class KnowledgeAiProperties {

    /**
     * 默认 embedding 模型标识（空则不执行向量化）
     */
    private String embeddingModel = "";

    /**
     * 是否启用向量写入（为 false 时跳过向量写入步骤）
     */
    private boolean vectorWriteEnabled = true;
}
