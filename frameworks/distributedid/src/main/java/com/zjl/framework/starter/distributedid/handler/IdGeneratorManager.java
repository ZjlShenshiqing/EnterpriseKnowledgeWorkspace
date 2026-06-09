/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.distributedid.handler;

import lombok.NonNull;
import com.zjl.framework.starter.distributedid.core.IdGenerator;
import com.zjl.framework.starter.distributedid.core.serviceid.DefaultServiceIdGenerator;
import com.zjl.framework.starter.distributedid.core.serviceid.ServiceIdGenerator;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * ID生成器管理
 *
 * @author zhangjlk
 * @date 2025/9/24 16:10
 */
public final class IdGeneratorManager {

    /**
     * ID生成器管理容器
     */
    private static Map<String, IdGenerator> MANAGER = new ConcurrentHashMap<>();

    /**
     * 注册默认ID生成器
     */
    static {
        MANAGER.put("default", new DefaultServiceIdGenerator());
    }

    /**
     * 注册 ID 生成器
     * 按资源名注册 ID 生成器，且只允许注册一次
     */
    public static void registerIdGenerator(@NonNull String resource, @NonNull IdGenerator generator) {
        IdGenerator actual = MANAGER.get(resource);
        if (actual != null) {
            return;
        }

        // 注册Id生成器到生成器管理容器上
        MANAGER.put(resource, generator);
    }

    /**
     * 根据资源名，安全地获取一个特定类型的 ID 生成器
     * @param resource 源
     * @return         ID生成器
     */
    public static ServiceIdGenerator getIdGenerator(@NonNull String resource) {
        return Optional.ofNullable(MANAGER.get(resource)).map(
                each -> (ServiceIdGenerator) each).orElse(null);
    }

    /**
     * 获取默认ID生成器
     * @return 默认生成器
     */
    public static ServiceIdGenerator getDefaultServiceIdGenerator() {
        return Optional.ofNullable(MANAGER.get("default")).map(
                each -> (ServiceIdGenerator) each
        ).orElse(null);
    }
}
