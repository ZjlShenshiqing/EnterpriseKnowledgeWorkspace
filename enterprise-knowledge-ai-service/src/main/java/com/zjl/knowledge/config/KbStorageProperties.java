package com.zjl.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库文件本地存储目录配置
 */
@Data
@ConfigurationProperties(prefix = "app.kb")
public class KbStorageProperties {

    /**
     * 上传文件保存目录
     */
    private String uploadDir = "./data/kb-uploads";
}
