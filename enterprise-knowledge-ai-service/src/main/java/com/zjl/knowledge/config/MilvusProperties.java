package com.zjl.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Milvus 连接与集合配置
 */
@Data
@ConfigurationProperties(prefix = "app.milvus")
public class MilvusProperties {

    /**
     * Milvus 服务地址，例如 http://localhost:19530
     */
    private String uri = "http://localhost:19530";

    /**
     * 向量集合名称
     */
    private String collection = "kb_chunk_embedding";

    /**
     * 向量维度（需与 embedding 模型维度一致，一期占位向量同维度）
     */
    private int vectorDimension = 128;

    /**
     * 启动时初始化集合失败是否直接中止应用（强制依赖 Milvus 时建议为 true）
     */
    private boolean failOnInit = true;
}
