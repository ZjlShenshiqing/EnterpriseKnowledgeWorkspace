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

    private final String prefix;
    private final AtomicInteger poolNumber = new AtomicInteger(1);
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    public AgentThreadFactory(String prefix) {
        this.prefix = prefix;
    }

    @Override
    public Thread newThread(Runnable r) {
        Thread thread = new Thread(r);
        thread.setName(prefix + poolNumber.get() + "-" + threadNumber.getAndIncrement());
        thread.setDaemon(false);
        return thread;
    }
}
