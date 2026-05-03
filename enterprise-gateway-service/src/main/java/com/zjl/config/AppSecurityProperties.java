package com.zjl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 安全相关配置项
 */
@Data
@ConfigurationProperties(prefix = "app.security")
public class AppSecurityProperties {

    /**
     * JWT 配置
     */
    private Jwt jwt = new Jwt();
    /**
     * 鉴权白名单配置
     */
    private Whitelist whitelist = new Whitelist();

    /**
     * JWT 配置项
     */
    @Data
    public static class Jwt {
        /**
         * HS256 secret（开发环境配置在 application.yml；生产需改为安全存储）
         */
        private String secret;

        /**
         * token 有效期（秒）
         */
        private long ttlSeconds = 7200;
    }

    /**
     * 鉴权白名单配置项
     */
    @Data
    public static class Whitelist {
        /**
         * 白名单路径（支持 Spring AntPath 风格）
         */
        private List<String> paths = new ArrayList<>();
    }
}

