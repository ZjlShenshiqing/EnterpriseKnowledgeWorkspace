/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.distributedid.toolkit;

import com.zjl.framework.starter.distributedid.core.IdGenerator;
import com.zjl.framework.starter.distributedid.core.snowflake.Snowflake;
import com.zjl.framework.starter.distributedid.core.snowflake.SnowflakeIdInfo;
import com.zjl.framework.starter.distributedid.handler.IdGeneratorManager;

/**
 * 分布式雪花算法 ID 生成器
 *
 * @author zhangjlk
 * @date 2025/9/24 16:14
 */
public final class SnowflakeIdUtil {

    /**
     * 自己定义雪花算法对象
     */
    private static Snowflake SNOWFLAKE;

    /**
     * 初始化雪花算法
     */
    public static void initSnowflake(Snowflake snowflake) {
        SnowflakeIdUtil.SNOWFLAKE = snowflake;
    }

    /**
     * 获取雪花算法实例
     */
    public static Snowflake getInstance() {
        return SNOWFLAKE;
    }

    /**
     * 获取雪花算法的下一个ID
     * @return ID
     */
    public static long nextId() {
        return SNOWFLAKE.nextId();
    }

    /**
     * 获取雪花算法的下一个字符串ID
     * @return
     */
    public static String nextIdStr() {
        return Long.toString(nextId());
    }

    /**
     * 解析雪花算法生成的ID为对象
     *
     * @param snowflakeId ID
     * @return            对象
     */
    public static SnowflakeIdInfo parseSnowflakeId(long snowflakeId) {
        return SNOWFLAKE.parseSnowflakeId(snowflakeId);
    }

    /**
     * 解析雪花算法生成的ID为对象（字符串版）
     * @param snowflakeId ID
     * @return            对象
     */
    public static SnowflakeIdInfo parseSnowflakeId(String snowflakeId) {
        return SNOWFLAKE.parseSnowflakeId(Long.parseLong(snowflakeId));
    }

    /**
     * 根据serviceId生成雪花算法ID
     * @param serviceId 服务id
     * @return 雪花算法id
     */
    public static long nextIdByService(String serviceId) {
        return IdGeneratorManager.getDefaultServiceIdGenerator().nextId(Long.parseLong(serviceId));
    }

    /**
     * 根据serviceId生成雪花算法ID
     * @param serviceId 服务id
     * @return 雪花算法id
     */
    public static String nextIdStrByService(String serviceId) {
        return IdGeneratorManager.getDefaultServiceIdGenerator().nextIdStr(Long.parseLong(serviceId));
    }

    /**
     * 根据serviceID，指定Id生成器生成ID
     * @param resource  指定ID生成器
     * @return          分布式ID
     */
    public static String nextIdByService(String resource, long serviceId) {
        return IdGeneratorManager.getIdGenerator(resource).nextIdStr(serviceId);
    }

    /**
     * 根据serviceID，指定Id生成器生成ID
     * @param resource  指定ID生成器
     * @return          分布式ID
     */
    public static String nextIdByService(String resource, String serviceId) {
        return IdGeneratorManager.getIdGenerator(resource).nextIdStr(serviceId);
    }

    /**
     * 解析snowflakeId为对象
     * @param snowflakeId 分布式雪花ID
     * @return            雪花ID对象
     */
    public static SnowflakeIdInfo parseSnowflakeServiceId(String snowflakeId) {
        return IdGeneratorManager.getDefaultServiceIdGenerator().parseSnowflakeId(Long.parseLong(snowflakeId));
    }

    /**
     * 解析snowflakeId为对象
     * @param resource  指定ID生成器
     * @param snowflakeId 分布式雪花ID
     * @return            雪花ID对象
     */
    public static SnowflakeIdInfo parseSnowflakeServiceId(String resource, String snowflakeId) {
        return IdGeneratorManager.getIdGenerator(resource).parseSnowflakeId(Long.parseLong(snowflakeId));
    }
}
