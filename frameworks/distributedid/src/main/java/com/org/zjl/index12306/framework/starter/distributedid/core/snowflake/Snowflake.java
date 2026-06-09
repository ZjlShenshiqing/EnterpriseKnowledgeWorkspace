/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.distributedid.core.snowflake;

import cn.hutool.core.date.SystemClock;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.IdUtil;
import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import io.netty.util.internal.StringUtil;
import lombok.Data;
import com.zjl.framework.starter.distributedid.core.IdGenerator;

import java.io.Serializable;
import java.util.Date;

/**
 * Twitter的Snowflake 算法<br>
 * 分布式系统中，有一些需要使用全局唯一ID的场景，有些时候我们希望能使用一种简单一些的ID，并且希望ID能够按照时间有序生成。
 *
 * <p>
 * snowflake的结构如下(每部分用-分开):<br>
 *
 * <pre>
 * 符号位（1bit）- 时间戳相对值（41bit）- 数据中心标志（5bit）- 机器标志（5bit）- 递增序号（12bit）
 * 0 - 0000000000 0000000000 0000000000 0000000000 0 - 00000 - 00000 - 000000000000
 * </pre>
 * <p>
 *
 * <p>
 * 第一位为未使用(符号位表示正数)，接下来的41位为毫秒级时间(41位的长度可以使用69年)<br>
 * 然后是5位datacenterId和5位workerId(10位的长度最多支持部署1024个节点）<br>
 * 最后12位是毫秒内的计数（12位的计数顺序号支持每个节点每毫秒产生4096个ID序号）
 * <p>
 * 并且可以通过生成的id反推出生成时间,datacenterId和workerId
 * <p>
 *
 * @author zhangjlk
 * @date 2025/9/24 16:12
 */
public class Snowflake implements Serializable, IdGenerator {

    private static final long serialVersionUID = 1L;

    /**
     * 默认的起始时间，为Thu, 04 Nov 2010 01:42:54 GMT
     */
    private static long DEFAULT_TWEPOCH = 1288834974657L;

    /**
     * 默认回拨时间
     *
     * 这是处理“时光倒流”的安全机制。
     * 问题： ID 是靠时间一直往前走来保证不重复的。但电脑有时会自动校时，导致时间突然往回跳了一点点（比如 1 秒）。
     *
     * 解决： 这个 2000L (就是 2 秒) 设置了一个容忍值。
     * 如果时间倒流在 2 秒以内，程序就原地等一下，等时间追上来再继续工作。
     * 如果时间倒流超过 2 秒，表示问题很严重，程序就直接报错，免得产生重复的 ID 造成天下大乱。
     *
     * 总结：它让 ID 生成器在遇到微小的时间问题时，更稳定、更不容易出错。
     */
    private static long DEFAULT_TIME_OFFSET = 2000L;

    private static final long WORKER_ID_BITS = 5L;

    private static final long DATA_CENTER_ID_BITS = 5L;

    // 最大支持机器节点数 0 ～ 31，一共32个机器 这个是与64位1异或运算得到的
    @SuppressWarnings({"PointlessBitwiseExpression"})
    private static final long MAX_WORKER_ID = -1L ^ (-1 << WORKER_ID_BITS);

    // 最大支持数据中心数 0 ～ 31
    @SuppressWarnings({"PointlessBitwiseExpression"})
    private static final long MAX_DATA_CENTER_ID = -1L ^ (-1 << DATA_CENTER_ID_BITS);

    // 序列号12位
    private static final long SEQUENCE_BITS = 12L;

    // 机器节点左移12位
    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;

    // 数据中心节点左移17位
    private static final long DATA_CENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;

    // 时间毫秒数左移22位
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATA_CENTER_ID_BITS;

    /**
     * 序列掩码，用来限定序列最大值不能超过4095
     *
     * SEQUENCE_MASK 就是一个数值限制器。它确保序列号在 0 到 4095 之间循环，一旦超过 4095，就自动归零
     * 这里的 & 是按位与运算。& 4095 的效果是：
     *
     * 如果 sequence 是 0 到 4094，+1 后再 & 4095，结果不变。
     * 如果 sequence 是 4095，+1 后变成 4096 (1000000000000)。
     *     1000000000000  (4096)
     *   & 0111111111111  (4095, 也就是 SEQUENCE_MASK)
     *   -----------------
     *   = 0000000000000  (0)
     */
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    /**
     * 初始化时间点
     */
    private final long twepoch;

    private final long workerId;

    private final long dataCenterId;

    private final boolean useSystemClock;

    /**
     * 允许的时钟回拨毫秒数
     */
    private final long timeOffset;

    /**
     * 低频场景：系统每毫秒只生成一个 ID（甚至更少），那么 sequence 始终从 0 开始，且不会递增（因为没到需要递增的程度）。
     * 如果 sequence 恒为 0，而 ID 的最低几位就是 sequence（比如低 12 位），那么 ID 的二进制末尾就是 ...0000，即 总是偶数。
     * 这在某些场景下可能引发哈希分布不均、数据库页分裂等问题。
     *
     * 此属性用于限定一个随机上限，在不同毫秒下生成序号时，给定一个随机数，避免偶数问题。
     * 为了解决“总是偶数”的问题，可以在每毫秒第一次生成 ID 时，不把 sequence 设为 0，而是设为一个随机值。
     * randomSequenceLimit 就是用来限制这个随机值的范围。
     * 例如：若 randomSequenceLimit = 100，则每次新毫秒开始时，sequence 会随机选一个 [0, 99] 之间的整数（注意：不包含 100）。
     */
    private final long randomSequenceLimit;

    /**
     * 自增序号，当在高频模式的情况下时，同一毫秒内生成N个ID，则这个序号在同一毫秒下，自增避免ID重复
     */
    private long sequence = 0L;

    /**
     * 上一次生成ID的时间戳，防止时间回拨导致ID重复
     */
    private long lastTimestamp = -1L;

    /**
     * 构造方法，使用自动生成的工作节点ID和数据中心ID
     */
    public Snowflake() {
        this(IdUtil.getWorkerId(IdUtil.getDataCenterId(MAX_DATA_CENTER_ID), MAX_WORKER_ID));
    }

    /**
     * @param workerId 工作机器Id
     */
    public Snowflake(long workerId) {
        this(workerId, IdUtil.getDataCenterId(MAX_DATA_CENTER_ID));
    }

    /**
     * @param workerId      工作机器ID
     * @param dataCenterId  数据中心ID
     */
    public Snowflake(long workerId, long dataCenterId) {
        this(workerId, dataCenterId, false);
    }

    /**
     * @param workerId       工作机器ID
     * @param dataCenterId   数据中心ID
     * @param useSystemClock 控制 Snowflake ID 生成器使用哪种时间源来获取当前时间戳
     */
    public Snowflake(long workerId, long dataCenterId, boolean useSystemClock) {
        this(null, workerId, dataCenterId, useSystemClock);
    }

    /**
     * @param epochdate        自定义的纪元时间（epoch），即时间戳的起始基准点。
     *                         生成 ID 时的时间戳 = 当前时间 - epochdate.getTime()。
     *                         不能为 {@code null}，且应小于当前系统时间，否则可能导致时间戳为负或溢出。
     * @param workerId         工作机器 ID
     * @param dataCenterId     数据中心 ID，
     * @param isUseSystemClock 是否直接使用系统时钟（{@link System#currentTimeMillis()}）。
     *                         <ul>
     *                             <li>{@code true}：直接使用系统时间，简单但可能受系统时间回拨影响；</li>
     *                             <li>{@code false}：使用内部保护时钟（如防回拨逻辑），更安全，适用于生产环境。</li>
     *                         </ul>
     */
    public Snowflake(Date epochdate, long workerId, long dataCenterId, boolean isUseSystemClock) {
        this(epochdate, workerId, dataCenterId, isUseSystemClock, DEFAULT_TIME_OFFSET);
    }

    /**
     * @param epochdate             自定义的纪元时间（epoch），即时间戳的起始基准点。
     *                              生成 ID 时的时间戳 = 当前时间 - epochdate.getTime()。
     *                              不能为 {@code null}，且应小于当前系统时间，否则可能导致时间戳为负或溢出。
     * @param workerId              工作机器 ID
     * @param dataCenterId          数据中心 ID
     * @param isUseSystemClock      是否直接使用系统时钟（{@link System#currentTimeMillis()}）。
     *                              <ul>
     *                                  <li>{@code true}：直接使用系统时间，简单但可能受系统时间回拨影响；</li>
     *                                  <li>{@code false}：使用内部保护时钟（如防回拨逻辑），更安全，适用于生产环境。</li>
     *                              </ul>
     * @param timeOffset            允许时间回拨的毫秒数
     */
    public Snowflake(Date epochdate, long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset) {
        this(epochdate, workerId, dataCenterId, isUseSystemClock, timeOffset, 0);
    }

    /**
     * @param epochdate             自定义的纪元时间（epoch），即时间戳的起始基准点。
     *                              生成 ID 时的时间戳 = 当前时间 - epochdate.getTime()。
     *                              不能为 {@code null}，且应小于当前系统时间，否则可能导致时间戳为负或溢出。
     * @param workerId              工作机器 ID
     * @param dataCenterId          数据中心 ID
     * @param isUseSystemClock      是否直接使用系统时钟（{@link System#currentTimeMillis()}）。
     *                              <ul>
     *                                  <li>{@code true}：直接使用系统时间，简单但可能受系统时间回拨影响；</li>
     *                                  <li>{@code false}：使用内部保护时钟（如防回拨逻辑），更安全，适用于生产环境。</li>
     *                              </ul>
     * @param timeOffset            允许时间回拨的毫秒数
     * @param randomSequenceLimit   设置一个随机上限，在不同的毫秒下生成序号的时候，给定一个随机数，来避免偶数问题，0表示无随机
     */
    public Snowflake(Date epochdate, long workerId, long dataCenterId, boolean isUseSystemClock, long timeOffset, long randomSequenceLimit) {
        this.twepoch = (null != epochdate) ? epochdate.getTime() : DEFAULT_TWEPOCH;
        /**
         * checkBetween方法：
         * 验证某个值是否在指定的闭区间 [min, max] 范围内。
         * 比如下面的这个workerId:
         * 1.如果 workerId 在 [0, MAX_WORKER_ID] 范围内 → 返回原值。
         * 2.如果 不在范围内 → 抛出异常（通常是 IllegalArgumentException）。
         */
        this.workerId = Assert.checkBetween(workerId, 0, MAX_WORKER_ID);
        this.dataCenterId = Assert.checkBetween(dataCenterId, 0, MAX_DATA_CENTER_ID);
        this.useSystemClock = isUseSystemClock;
        this.timeOffset = timeOffset;
        this.randomSequenceLimit = Assert.checkBetween(randomSequenceLimit, 0, SEQUENCE_MASK);
    }

    /**
     * 根据Snowflake的ID，获取机器ID
     *
     * id >> WORKER_ID_SHIFT：右移5位，那么最后的几位就是workid了
     *
     * WORKER_ID_BITS 生成掩码，WORKER_ID_BITS 位为 1，其他都是0，这样按位与运算，得到的就是机器ID
     *
     *
     * @param id  snowflake算法生成的ID
     * @return    所属的机器ID
     */
    public long getWorkerId(long id) {
        return id >> WORKER_ID_SHIFT & ~(-1L << WORKER_ID_BITS);
    }

    /**
     * 根据Snowflake的ID，获取数据中心ID
     *
     * @param id  snowflake算法生成的ID
     * @return    所属的数据中心ID
     */
    public long getDataCenterId(long id) {
        return id >> DATA_CENTER_ID_SHIFT & ~(-1L << DATA_CENTER_ID_BITS);
    }

    /**
     * 根据Snowflake的ID，获取生成时间
     *
     * @param id  snowflake算法生成的ID
     * @return    生成时间
     */
    public long getGenerateDateTime(long id) {
        // 因为 Snowflake 的时间戳是 相对于 twepoch 的毫秒数，所以要加上 twepoch 才能得到 标准 Unix 时间戳（毫秒）
        return (id >> TIMESTAMP_LEFT_SHIFT & ~(-1L << 41L)) + twepoch;
    }

    /**
     * 下一个ID
     *
     * @return ID
     */
    public synchronized long nextId() {
        long timestamp = genTime();
        if (timestamp < this.lastTimestamp) {
            if (this.lastTimestamp - timestamp < timeOffset) {
                // 容忍指定时间的回拨
                timestamp = lastTimestamp; // 将生成时间回拨到上一次的时间,再进行时间的生成
            } else {
                // 服务器时间有问题，报错
                throw new IllegalArgumentException(StrUtil.format("Clock moved backwards. Refusing to generate id for {}milliseconds", timestamp - lastTimestamp));
            }
        }

        // 如果当前生成 ID 的时间戳 和上一次生成 ID 的时间戳相同（即在同一毫秒内），就需要使用序列号来区分不同的 ID。
        if (timestamp == this.lastTimestamp) {
            /**
             * 将序列号加 1，并用 & SEQUENCE_MASK 保证结果在 0 ~ 4095 范围内。
             * 例如：如果 this.sequence 是 4095，加 1 后变成 4096，
             * 4096 & 4095 = 0（因为 4095 的二进制是 12 个 1，4096 是 1 后跟 12 个 0，按位与后为 0）。
             */
            final long sequence = (this.sequence + 1) & SEQUENCE_MASK;
            // 如果 sequence 变成了 0，说明刚刚发生了序列号溢出
            if (sequence == 0) {
                // 循环获取当前时间，直到时间戳大于 lastTimestamp，确保进入新的毫秒，这样新的毫秒时间戳可以是0
                timestamp = tillNextMillis(lastTimestamp);
            }

            // 更新当前序列号
            this.sequence = sequence;
        } else {// 进入新的毫秒：重置序列号
            // 支持“随机起始序列号”（用于避免多实例在相同毫秒生成相同 ID）
            if (randomSequenceLimit > 1) {
                // 例如 randomSequenceLimit = 100，则 sequence ∈ [0, 99]
                sequence = RandomUtil.randomLong(randomSequenceLimit);
            } else {
                // 默认从0开始
                sequence = 0L;
            }
        }
        // 更新最后使用的时间戳
        lastTimestamp = timestamp;
        // 更新雪花ID
        return ((timestamp - twepoch) <<  TIMESTAMP_LEFT_SHIFT) | (dataCenterId << DATA_CENTER_ID_SHIFT) | (workerId << WORKER_ID_SHIFT) | sequence;
    }

    /**
     * 字符串形式的下一个ID
     *
     * @return  ID
     */
    public String nextIdStr() {
        return Long.toString(nextId());
    }

    /**
     * 循环等待下一个时间
     *
     * @param lastTimestamp 上一次生成ID的时间戳
     * @return              下一个时间
     */
    private long tillNextMillis(long lastTimestamp) {
        long timestamp = genTime();
        // 忙等待（busy-waiting）：只要当前时间仍等于 lastTimestamp（即还没进入下一毫秒），就不断重新获取时间。
        while (timestamp == lastTimestamp) {
            timestamp = genTime();
        }

        if (timestamp < lastTimestamp) {
            // 新的时间戳比上一次时间倒退了，报错
            throw new IllegalArgumentException(StrUtil.format("Clock moved backwards. Refusing to generate id for {}", lastTimestamp - timestamp));
        }

        return timestamp;
    }

    /**
     * 获取当前时间戳（毫秒）。
     * <p>
     * 该方法根据 {@link #useSystemClock} 标志决定使用哪种时间源：
     * <ul>
     *   <li>若为 {@code true}，调用 {@link SystemClock#now()} —— Hutool 提供的高性能时钟，通过后台线程定时刷新时间戳，避免频繁系统调用，在高并发场景下性能显著优于 {@code System.currentTimeMillis()}。</li>
     *   <li>若为 {@code false}，回退至 JDK 原生的 {@link System#currentTimeMillis()} —— 精确但存在锁竞争开销，适合低频或对精度要求极高的场景。</li>
     * </ul>
     *
     * @return 当前时间戳，单位：毫秒（自 Unix 纪元 1970-01-01T00:00:00Z 起）
     */
    private long genTime() {
        return this.useSystemClock ? SystemClock.now() : System.currentTimeMillis();
    }

    /**
     * 解析雪花算法生成的ID为对象
     *
     * @param snowflakeId   雪花算法ID
     * @return              雪花算法对象
     */
    public SnowflakeIdInfo parseSnowflakeId(long snowflakeId) {
        SnowflakeIdInfo snowflakeIdInfo = SnowflakeIdInfo.builder()
                .sequence((int) (snowflakeId & ~(-1L << SEQUENCE_BITS)))
                .workerId((int) ((snowflakeId >> WORKER_ID_SHIFT) & ~(-1L << DATA_CENTER_ID_BITS)))
                .dataCenterId((int) ((snowflakeId >> DATA_CENTER_ID_SHIFT) & ~(-1L << DATA_CENTER_ID_BITS)))
                .timestamp((snowflakeId >> TIMESTAMP_LEFT_SHIFT) + twepoch)
                .build();

        return snowflakeIdInfo;
    }
}
