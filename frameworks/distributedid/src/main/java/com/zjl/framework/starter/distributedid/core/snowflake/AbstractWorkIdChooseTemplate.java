/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.distributedid.core.snowflake;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import com.zjl.framework.starter.distributedid.toolkit.SnowflakeIdUtil;

/**
 * 雪花算法模版生成
 *
 * @author zhangjlk
 * @date 2025/9/24 16:13
 */
@Slf4j
public abstract class AbstractWorkIdChooseTemplate {

    /**
     * 是否使用 SystemLock 来获取当前时间戳
     *
     * 使用 @Value 注解从配置文件注入属性值
     */
    @Value("${framework.distributed.id.snowflake.is-use-system-lock:false}")
    private boolean isUseSystemLock;

    /**
     * 根据自定义的策略获取WorkerID生成器
     */
    protected abstract WorkIdWrapper chooseWorkId();

    /**
     * 获取WorkerId并初始化雪花ID
     */
    public void chooseAndInit() {
        // 模版方法模式：通过抽象方法获取WorkerID包装器创建雪花算法
        WorkIdWrapper workIdWrapper = chooseWorkId();
        long workId = workIdWrapper.getWorkerId();
        long dataCenterId = workIdWrapper.getDatacenterId();
        // 初始化雪花算法
        Snowflake snowflake = new Snowflake(workId, dataCenterId, isUseSystemLock);
        log.info("Snowflake 类型: {}, workId: {}, dataCenterId: {}", this.getClass().getSimpleName(), workId, dataCenterId);
        SnowflakeIdUtil.initSnowflake(snowflake);
    }
}
