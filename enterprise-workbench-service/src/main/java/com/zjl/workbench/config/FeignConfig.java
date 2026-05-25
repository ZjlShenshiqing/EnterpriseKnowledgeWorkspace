package com.zjl.workbench.config;

import feign.Logger;
import org.springframework.context.annotation.Bean;

/**
 * OpenFeign 通用配置
 */
public class FeignConfig {

    @Bean
    Logger.Level feignLoggerLevel() {
        return Logger.Level.BASIC;
    }
}
