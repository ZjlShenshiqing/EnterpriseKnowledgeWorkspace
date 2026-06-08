package com.zjl.knowledge.config;

import com.zjl.knowledge.service.rerank.RerankStrategy;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.rag.rerank")
public class RagRerankProperties {

    /** 是否启用 rerank */
    private boolean enabled = true;

    /** rerank 策略，默认本地特征 */
    private RerankStrategy strategy = RerankStrategy.LOCAL_FEATURE;

    /** 候选数量上限 */
    private int candidateLimit = 50;

    /** rerank 超时时间（毫秒） */
    private long timeoutMs = 800;

    /** 失败时是否回退到原始召回顺序 */
    private boolean fallbackToOriginalOrder = true;
}
