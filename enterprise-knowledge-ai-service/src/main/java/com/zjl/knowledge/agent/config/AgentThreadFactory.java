package com.zjl.knowledge.agent.config;

import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Agent 线程工厂
 *
 * <p>自定义线程工厂，用于创建带有统一命名前缀的线程。
 * 线程名称格式：{prefix}{poolNumber}-{threadNumber}</p>
 *
 * @see java.util.concurrent.ThreadFactory
 */
public class AgentThreadFactory implements ThreadFactory {

    /**
     * 线程名前缀，用于区分不同业务线程池。
     * 最终线程名格式：{prefix}{poolNumber}-{threadNumber}
     * 例如：agent-worker-1-3
     */
    private final String prefix;

    /**
     * 线程池编号
     *
     * 注意：当前实现中 poolNumber 是实例字段，且没有自增逻辑，
     * 所以每个 AgentThreadFactory 内创建的线程池编号始终是 1。
     * 如果希望全局区分多个线程池，应改成 static 全局计数器。
     */
    private final AtomicInteger poolNumber = new AtomicInteger(1);

    /**
     * 当前线程工厂创建的线程编号
     * 用于保证同一个线程池内线程名递增且可读。
     */
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public AgentThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);

        // 设置可识别的线程名，便于日志排查、链路追踪和线程 dump 分析。
        thread.setName(prefix + poolNumber.get() + "-" + threadNumber.getAndIncrement());

        // 使用非守护线程，避免任务尚未执行完成时 JVM 直接退出。
        thread.setDaemon(false);

        return thread;
    }
}
