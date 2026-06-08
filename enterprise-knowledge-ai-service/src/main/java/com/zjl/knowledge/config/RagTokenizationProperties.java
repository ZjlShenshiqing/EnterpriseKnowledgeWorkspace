package com.zjl.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * RAG 中文分词配置。
 */
@Data
@ConfigurationProperties(prefix = "app.rag.tokenization")
public class RagTokenizationProperties {

    /**
     * 是否启用 IK 分词。
     */
    private boolean enabled = true;

    /**
     * IK 分词失败时是否启用本地字符级回退。
     */
    private boolean fallbackEnabled = true;
}
