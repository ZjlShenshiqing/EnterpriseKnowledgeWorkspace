/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.distributedid.core.snowflake;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * WorkId 包装器
 *
 * WorkIdWrapper 是一个用于封装 Snowflake 分布式 ID 生成器所需机器标识信息的简单 Java Bean，
 * 在分布式系统中用于确保生成的 ID 全局唯一。
 *
 * @author zhangjlk
 * @date 2025/9/24 16:11
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class WorkIdWrapper {

    /**
     * 工作机器ID
     */
    private long workerId;

    /**
     * 数据中心ID
     */
    private long datacenterId;
}
