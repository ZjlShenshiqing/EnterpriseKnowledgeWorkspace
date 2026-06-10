/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.distributedid.config;

import com.zjl.common.toolkit.ApplicationContextHolder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import com.zjl.framework.starter.distributedid.core.snowflake.LocalRedisWorkIdChoose;
import com.zjl.framework.starter.distributedid.core.snowflake.RandomWorkIdChoose;

/**
 * 分布式ID自动装配
 *
 * @author zhangjlk
 * @date 2025/9/24 16:09
 */
@Import(ApplicationContextHolder.class)
public class DistributedIdAutoConfiguration {

    @Bean
    @ConditionalOnProperty("spring.data.redis.host")
    public LocalRedisWorkIdChoose redisWorkIdChoose() {
        return new LocalRedisWorkIdChoose();
    };

    @Bean
    @ConditionalOnMissingBean(LocalRedisWorkIdChoose.class)
    public RandomWorkIdChoose randomWorkIdChoose() {
        return new RandomWorkIdChoose();
    }
}
