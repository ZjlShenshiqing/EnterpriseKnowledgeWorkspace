package com.zjl.framework.starter.log.config;

import com.zjl.framework.starter.log.core.ILogPrintAspect;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 日志自动装配
 */
@Configuration
public class LogAutoConfiguration {

    @Bean
    public ILogPrintAspect iLogPrintAspect() {
        return new ILogPrintAspect();
    }
}