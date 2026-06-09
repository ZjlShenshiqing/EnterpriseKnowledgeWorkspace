/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.distributedid.core.serviceid;

import com.zjl.framework.starter.distributedid.core.IdGenerator;
import com.zjl.framework.starter.distributedid.core.snowflake.SnowflakeIdInfo;

/**
 * 业务 ID 生成器接口。
 * <p>
 * 在通用 {@link IdGenerator} 的基础上，提供基于服务标识（serviceId）的 ID 生成能力，
 * 便于按照业务线/服务维度区分并发写入源，减少冲突并支持按服务解读 ID 含义。
 * </p>
 *
 * <p>说明：</p>
 * <ul>
 *     <li>提供 long 与 String 两种 serviceId 入参的重载。</li>
 *     <li>{@code parseSnowflakeId} 用于解析雪花算法生成的 ID，获取各组成部分信息。</li>
 * </ul>
 *
 * @author zhangjlk
 * @date 2025/9/24 16:11
 */
public interface ServiceIdGenerator extends IdGenerator {

    /**
     * 生成下一个业务维度的长整型 ID（基于 long 型 serviceId）。
     *
     * @param serviceId 服务标识（long）
     * @return 全局唯一 ID（long）
     */
    default long nextId(long serviceId) {
        return 0L;
    }

    /**
     * 生成下一个业务维度的长整型 ID（基于字符串型 serviceId）。
     *
     * @param serviceId 服务标识（字符串）
     * @return 全局唯一 ID（long）
     */
    default long nextId(String serviceId) {
        return 0L;
    }

    /**
     * 生成下一个业务维度的字符串 ID（基于 long 型 serviceId）。
     *
     * @param serviceId 服务标识（long）
     * @return 全局唯一 ID（字符串）
     */
    default String nextIdStr(long serviceId) {
        return null;
    }

    /**
     * 生成下一个业务维度的字符串 ID（基于字符串型 serviceId）。
     *
     * @param serviceId 服务标识（字符串）
     * @return 全局唯一 ID（字符串）
     */
    default String nextIdStr(String serviceId) {
        return null;
    }

    /**
     * 解析基于雪花算法生成的 ID，返回各组成部分信息。
     * <p>
     * 典型组成包含时间戳、数据中心/机器标识、序列号等。
     * </p>
     *
     * @param serviceId 服务标识（long）
     * @return 雪花 ID 解析信息
     */
    SnowflakeIdInfo parseSnowflakeId(long serviceId);
}
