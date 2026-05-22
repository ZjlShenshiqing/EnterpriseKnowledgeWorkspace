package com.zjl.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识库文件存储配置 — 支持本地磁盘和 S3 兼容对象存储
 */
@Data
@ConfigurationProperties(prefix = "app.kb.storage")
public class KbStorageProperties {

    /**
     * 存储类型：local / s3
     */
    private String type = "local";

    /**
     * 本地存储上传目录（仅 type=local 时生效）
     */
    private String uploadDir = "./data/kb-uploads";

    /**
     * S3 兼容存储配置（仅 type=s3 时生效）
     */
    private S3 s3 = new S3();

    @Data
    public static class S3 {

        /**
         * S3 兼容端点（Supabase: https://xxx.supabase.co/storage/v1/s3）
         */
        private String endpoint = "";

        /**
         * 区域（Supabase 填任意值即可，如 auto）
         */
        private String region = "auto";

        /**
         * Access Key
         */
        private String accessKey = "";

        /**
         * Secret Key
         */
        private String secretKey = "";

        /**
         * 存储桶名称
         */
        private String bucket = "";
    }
}
