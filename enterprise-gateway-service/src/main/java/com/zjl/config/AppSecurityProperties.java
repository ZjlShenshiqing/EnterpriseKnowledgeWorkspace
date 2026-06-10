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
     * 鉴权白名单配置
     */
    private Whitelist whitelist = new Whitelist();

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
