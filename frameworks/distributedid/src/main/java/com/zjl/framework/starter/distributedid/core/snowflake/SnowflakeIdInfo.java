/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.distributedid.core.snowflake;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 雪花算法（Snowflake）ID 解析信息对象
 * <p>
 * 用于承载对雪花 ID 的拆解结果，便于业务对 ID 进行可解释化分析（如追踪生成时间、机房/机器来源等）。
 * 典型雪花 ID 由时间戳、数据中心标识、机器标识、序列号等部分组成。
 * </p>
 *
 * <p>说明：</p>
 * <ul>
 *     <li>timestamp：生成时间戳（毫秒）。</li>
 *     <li>dataCenterId：数据中心标识（通常映射为机房或区域）。</li>
 *     <li>workerId：机器标识（节点/实例编号）。</li>
 *     <li>sequence：同一毫秒内的自增序列，解决并发冲突。</li>
 *     <li>gene：保留/附加位，用于业务自定义含义（如业务线标识）</li>
 * </ul>
 *
 * <p>注意：不同实现的位宽分配可能不同，但语义基本一致。</p>
 *
 * @author zhangjlk
 * @date 2025/9/24 16:14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SnowflakeIdInfo {

    /**
     * 生成时间戳（毫秒）。
     */
    private Long timestamp;

    /**
     * 机器标识（节点/实例）。
     */
    private Integer workerId;

    /**
     * 数据中心标识（机房/区域）。
     */
    private Integer dataCenterId;

    /**
     * 同一毫秒内的序列号。
     */
    private Integer sequence;

    /**
     * 通过基因算法生成的序号，目前未实现
     *
     * 基本解释：在雪花算法的“序列区”里预留出若干位，
     * 作为“业务自定义标识位”（gene）。这些位与序列位共享总位宽（如共12bit），用来编码业务标签、租户、环境等信息
     */
    private Integer gene;
}
