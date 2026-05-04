package com.zjl.knowledge.config;

import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Milvus 客户端 Bean 定义
 */
@Configuration
public class MilvusClientConfiguration {

    /**
     * 创建 Milvus 客户端
     *
     * @param properties Milvus 配置
     * @return 客户端实例
     */
    @Bean
    public MilvusClientV2 milvusClientV2(MilvusProperties properties) {
        ConnectConfig connectConfig = ConnectConfig.builder()
                .uri(properties.getUri())
                .build();
        return new MilvusClientV2(connectConfig);
    }
}
