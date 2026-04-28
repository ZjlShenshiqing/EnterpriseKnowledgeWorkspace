package com.zjl.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 网关相关配置项（限流、IP 黑白名单）
 */
@Data
@ConfigurationProperties(prefix = "app.gateway")
public class AppGatewayProperties {

    /**
     * IP 访问控制配置
     */
    private Ip ip = new Ip();
    /**
     * 简易限流配置
     */
    private RateLimit rateLimit = new RateLimit();

    /**
     * IP 黑白名单配置项
     */
    @Data
    public static class Ip {
        /**
         * 黑名单 IP（精确匹配）
         */
        private List<String> blacklist = new ArrayList<>();

        /**
         * 白名单 IP：若非空，则只允许该列表访问
         */
        private List<String> whitelist = new ArrayList<>();
    }

    /**
     * 简易限流配置项
     */
    @Data
    public static class RateLimit {
        /**
         * 是否启用限流
         */
        private boolean enabled = true;
        /**
         * 窗口内允许的请求数
         */
        private int requests = 60;
        /**
         * 时间窗口（秒）
         */
        private int windowSeconds = 60;
    }
}

