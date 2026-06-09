package com.zjl.knowledge.agent.config;

import com.alibaba.ttl.threadpool.TtlExecutors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Agent 线程池配置
 *
 * <p>使用阿里 TTL（TransmittableThreadLocal）包装线程池，
 * 确保 UserContext 等 ThreadLocal 变量能正确传递到异步任务中。</p>
 *
 * <p>主要优化：</p>
 * <ul>
 *   <li>替换直接 new Thread()，避免线程耗尽</li>
 *   <li>队列缓冲，应对突发流量</li>
 *   <li>TTL 保证上下文传递</li>
 *   <li>CallerRunsPolicy 保证任务不丢失</li>
 * </ul>
 *
 * @see AgentProperties.ThreadPool
 * @see com.alibaba.ttl.TtlExecutors
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AgentProperties.class)
public class AgentThreadPoolConfig {

    /**
     * Agent 线程池 Bean 名称
     */
    public static final String AGENT_EXECUTOR_BEAN_NAME = "agentExecutor";

    private final AgentProperties agentProperties;

    /**
     * 创建 TTL 线程池
     *
     * <p>线程池参数由 {@code app.agent.thread-pool.*} 配置：</p>
     * <ul>
     *   <li>coreSize: 核心线程数，默认 10</li>
     *   <li>maxSize: 最大线程数，默认 30</li>
     *   <li>queueCapacity: 队列容量，默认 100</li>
     *   <li>keepAliveSeconds: 空闲存活时间，默认 120 秒</li>
     * </ul>
     *
     * <p>拒绝策略使用 CallerRunsPolicy，即当线程池满时，由调用线程执行任务，
     * 这样可以保证任务不会丢失，同时对请求方有一定的限流效果。</p>
     *
     * @return TTL 包装后的 ExecutorService
     */
    @Bean(AGENT_EXECUTOR_BEAN_NAME)
    public ExecutorService agentExecutor() {
        AgentProperties.ThreadPool config = agentProperties.getThreadPool();

        log.info("初始化 Agent 线程池: coreSize={}, maxSize={}, queueCapacity={}, keepAliveSeconds={}",
                config.getCoreSize(),
                config.getMaxSize(),
                config.getQueueCapacity(),
                config.getKeepAliveSeconds());

        // 创建原生线程池
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                config.getCoreSize(),
                config.getMaxSize(),
                config.getKeepAliveSeconds(),
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(config.getQueueCapacity()),
                new AgentThreadFactory(config.getThreadNamePrefix()),
                createRejectedExecutionHandler()
        );

        // 用 TTL 包装，确保 UserContext 等 ThreadLocal 正确传递
        return TtlExecutors.getTtlExecutorService(executor);
    }

    /**
     * 创建拒绝策略处理器
     *
     * <p>使用 CallerRunsPolicy，当线程池满时由调用线程执行任务。
     * 这保证了：</p>
     * <ul>
     *   <li>任务不会丢失</li>
     *   <li>对请求方有背压效果</li>
     *   <li>系统不会因为任务积压而崩溃</li>
     * </ul>
     *
     * @return 拒绝策略处理器
     */
    private RejectedExecutionHandler createRejectedExecutionHandler() {
        return new ThreadPoolExecutor.CallerRunsPolicy() {
            @Override
            public void rejectedExecution(Runnable r, ThreadPoolExecutor e) {
                log.warn("Agent 线程池已满，队列积压: activeCount={}, poolSize={}, queueSize={}",
                        e.getActiveCount(),
                        e.getPoolSize(),
                        e.getQueue().size());
                super.rejectedExecution(r, e);
            }
        };
    }
}
