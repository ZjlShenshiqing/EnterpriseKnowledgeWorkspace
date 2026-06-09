/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.distributedid.core.serviceid;

import com.zjl.framework.starter.distributedid.core.IdGenerator;
import com.zjl.framework.starter.distributedid.core.snowflake.Snowflake;
import com.zjl.framework.starter.distributedid.core.snowflake.SnowflakeIdInfo;
import com.zjl.framework.starter.distributedid.toolkit.SnowflakeIdUtil;

/**
 * 默认业务 ID 生成器实现。
 * <p>
 * 基于雪花算法（Snowflake）的位布局，在“序列区”中划分了业务位（gene）与实际序列位（sequence），
 * 以便在同一时间戳下既能携带业务标识，又能支持一定并发的自增序列能力。
 * </p>
 *
 * <p>位布局说明（自低位到高位）：</p>
 * <ul>
 *     <li>sequence（实际序列位）：{@link #SEQUENCE_ACTUAL_BITS} bit</li>
 *     <li>gene（业务标识位）：{@link #SEQUENCE_BIZ_BITS} bit</li>
 *     <li>workerId（机器标识）：{@link #WORKER_ID_BITS} bit</li>
 *     <li>dataCenterId（数据中心标识）：{@link #DATA_CENTER_ID_BITS} bit</li>
 *     <li>timestamp（时间戳偏移）：其余高位</li>
 * </ul>
 *
 * <p>注意：</p>
 * <ul>
 *     <li>gene 与 sequence 共用 {@link #SEQUENCE_BITS} bit（例如 12 位），两者之和固定为该常量。</li>
 *     <li>gene 位数越大，可表达的业务标签越多，但实际可用序列位越少，从而降低单毫秒的最大并发容量。</li>
 *     <li>本实现中 {@code serviceId} 通过哈希后映射到 gene 可表示的范围内（0 ~ 2^{gene}-1）。</li>
 * </ul>
 *
 * @author zhangjlk
 * @date 2025/9/24 16:10
 */
public final class DefaultServiceIdGenerator implements ServiceIdGenerator {

    /**
     * 底层通用 ID 生成器（用于生成雪花 ID 主体）。
     */
    private final IdGenerator idGenerator;

    /**
     * gene 可表示的最大取值（= 2^{gene bit 长度}）。
     */
    private long maxBizIdBitsLen;

    /**
     * 使用默认 gene 位数（{@link #SEQUENCE_BIZ_BITS}）的构造方法。
     * 便于快速初始化默认策略。
     */
    public DefaultServiceIdGenerator() {
        this(SEQUENCE_BIZ_BITS);
    }

    /**
     * 指定 gene 位数的构造方法。
     *
     * @param serviceIdBitLen gene 位数（业务位长度，这个就是业务标识）
     */
    public DefaultServiceIdGenerator(long serviceIdBitLen) {
        idGenerator = SnowflakeIdUtil.getInstance();

        /**
         * 二进制中，如果你有 n 个位，你可以表示 2^n 个不同的值（从 0 到 2^(n-1)）。
         *
         * 所以，maxBizIdBitsLen 存储的不是位的长度，而是由这些位所能表示的数值范围（或者说，能容纳多少个不同的业务ID）。
         * 举例： 如果 serviceIdBitLen 是 4，那么 maxBizIdBitsLen 就是 2^4
         *  =16。这意味着你可以区分 16 个不同的业务，它们的 gene 值会是 0, 1, 2, ..., 15。
         */
        this.maxBizIdBitsLen = (long) Math.pow(2, serviceIdBitLen);
    }

    /**
     * 生成带业务标识的长整型 ID。
     * <p>
     * 流程：
     * </p>
     * <ol>
     *     <li>对 {@code serviceId} 进行哈希并取模到 gene 的取值范围内，得到 gene 值。</li>
     *     <li>调用底层 {@link #idGenerator} 生成基础雪花 ID。</li>
     *     <li>使用按位或（OR）将 gene 合并到雪花 ID 的“序列区”低位。</li>
     * </ol>
     *
     * @param serviceId 业务标识（long）
     * @return 全局唯一 ID（long）
     */
    @Override
    public long nextId(long serviceId) {
        long id = Math.abs(Long.valueOf(serviceId).hashCode()) % (this.maxBizIdBitsLen);
        long nextId = idGenerator.nextId();
        // 将 gene 值合并到标准 ID 的低位
        return nextId | id;
    }

    /**
     * 生成带业务标识的字符串 ID。
     *
     * @param serviceId 业务标识（long）
     * @return 全局唯一 ID（字符串）
     */
    @Override
    public String nextIdStr(long serviceId) {
        return Long.toString(nextId(serviceId));
    }

    /**
     * 解析雪花 ID，抽取各组成部分信息。
     * <p>
     * 使用位移与掩码操作分别提取：workerId、dataCenterId、timestamp、sequence、gene。
     * </p>
     *
     * @param snowflakeId 雪花 ID（long）
     * @return 解析结果 {@link SnowflakeIdInfo}
     */
    @Override
    public SnowflakeIdInfo parseSnowflakeId(long snowflakeId) {
        SnowflakeIdInfo snowflakeIdInfo = SnowflakeIdInfo.builder()
                /**
                 * 从一个完整的 Snowflake ID 中提取出 Worker ID
                 *
                 * 第一步：
                 * snowflakeId >> WORKER_ID_SHIFT
                 * >> 是右移运算符。它把 snowflakeId 的二进制表示向右移动 WORKER_ID_SHIFT (也就是 12) 位。
                 * 目的： Worker ID 区域的右边是 12 位的序列号。为了让 Worker ID 区域移动到最右边，我们需要向右移动 12 位。
                 * 例子 (简化版): 假设 ID 是 ...[worker_id][sequence]，向右移 12 位后就变成了 ...[unused][worker_id]
                 *
                 * 第二步：
                 * & ~(-1L << WORKER_ID_BITS)
                 * & 是按位与运算符。
                 * ~(-1L << WORKER_ID_BITS) 是一个巧妙的技巧，用来生成一个“掩码” (mask)。
                 * -1L 的二进制表示是 64 个 1 (1111...1111)。
                 * << 是左移运算符。-1L << WORKER_ID_BITS 就是把 64 个 1 向左移动 5 位，右边补 0，结果是 111...111100000。
                 * ~ 是按位取反运算符。把上面的结果取反，就得到 000...000011111。这个结果就是一个低 5 位是 1，其余位都是 0 的数字。这个数字就是掩码。
                 *
                 * 最后一步：按位与计算
                 * 目的： 将第一步移位后的结果与这个掩码 000...000011111 做“按位与”运算。& 运算的规则是：两个位都是 1，结果才是 1，否则是 0。
                 * 结果： 只有右边的 5 位 (Worker ID) 被保留下来，其他更高位的都变成了 0。这样就精确地提取出了 Worker ID 的值。
                 */
                .workerId((int) ((snowflakeId >> WORKER_ID_SHIFT) & ~(-1L << WORKER_ID_BITS)))
                .dataCenterId((int) ((snowflakeId >> DATA_CENTER_ID_BITS) & ~(-1L << DATA_CENTER_ID_BITS)))
                .timestamp((snowflakeId >> TIMESTAMP_LEFT_SHIFT) + DEFAULT_TWEPOCH)
                .sequence((int) ((snowflakeId >> SEQUENCE_BIZ_BITS) & ~(-1L << SEQUENCE_ACTUAL_BITS)))
                .gene((int) ((snowflakeId & ~(-1 << SEQUENCE_BIZ_BITS))))
                .build();
        return snowflakeIdInfo;
    }

    /**
     * 机器标识位宽（bit）。
     */
    private static final long WORKER_ID_BITS = 5L;

    /**
     * 数据中心标识位宽（bit）。
     */
    private static final long DATA_CENTER_ID_BITS = 5L;

    /**
     * 序列区总位宽（gene + sequence）（bit）。
     */
    private static final long SEQUENCE_BITS = 12L;

    /**
     * 实际序列位宽（bit）。
     */
    private static final long SEQUENCE_ACTUAL_BITS = 8L;

    /**
     * 业务基因位宽（bit）。
     */
    private static final long SEQUENCE_BIZ_BITS = 4L;

    /**
     * workerId 左移位数（位于 sequence 区高位之上）。
     */
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    /**
     * 起始时间戳（Twitter Snowflake 默认 epoch）。
     */
    private static long DEFAULT_TWEPOCH = 1288834974657L;

    /**
     * dataCenterId 左移位数（位于 workerId 之上）。
     */
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    /**
     * 时间戳左移位数（位于 dataCenterId 之上）。
     */
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;
}
