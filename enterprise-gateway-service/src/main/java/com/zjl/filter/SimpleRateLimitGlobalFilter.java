package com.zjl.filter;

import com.zjl.gateway.response.ApiResponseWriter;
import com.zjl.common.trace.TraceIdHolder;
import com.zjl.config.AppGatewayProperties;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易限流全局过滤器（按 IP、固定窗口计数）
 *
 * TODO：后续需要进行改进
 *
 * <p>算法：固定窗口计数</p>
 * <ul>
 *   <li>维度：按客户端 IP 作为 key</li>
 *   <li>窗口：按秒对齐的固定窗口 windowSeconds</li>
 *   <li>阈值：窗口内最大请求数 requests</li>
 * </ul>
 *
 * <p>说明：MVP 阶段使用内存计数器 多实例或生产环境建议替换为 Redis 等集中式限流</p>
 */
@Slf4j
@Component
@EnableConfigurationProperties(AppGatewayProperties.class)
public class SimpleRateLimitGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 单窗口计数器
     *
     * @param windowStartEpochSec 窗口起始秒
     * @param count 当前计数
     */
    private record WindowCounter(long windowStartEpochSec, int count) {}

    /**
     * 网关配置项
     */
    private final AppGatewayProperties props;

    /**
     * 统一 JSON 输出器
     */
    private final ApiResponseWriter writer;

    /**
     * 内存计数器：key=ip
     */
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    /**
     * 构造器注入
     *
     * @param props 网关配置项
     * @param writer JSON 输出器
     */
    public SimpleRateLimitGlobalFilter(AppGatewayProperties props, ApiResponseWriter writer) {
        this.props = props;
        this.writer = writer;
    }

    /**
     * 限流主逻辑
     *
     * <p>步骤</p>
     * <ul>
     *   <li>若未开启限流则直接放行</li>
     *   <li>取客户端 IP 作为计数 key 若为空则使用 unknown</li>
     *   <li>按 windowSeconds 计算当前窗口起点 windowStart</li>
     *   <li>使用 ConcurrentHashMap.compute 原子更新窗口计数</li>
     *   <li>若计数超过阈值则返回 429 并输出统一结构</li>
     * </ul>
     *
     * @param exchange exchange
     * @param chain chain
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if (!props.getRateLimit().isEnabled()) {
            return chain.filter(exchange);
        }

        String key = ip(exchange);
        if (key == null) {
            key = "unknown";
        }

        int limit = props.getRateLimit().getRequests();
        int windowSeconds = props.getRateLimit().getWindowSeconds();
        long nowSec = Instant.now().getEpochSecond();

        /**
         * 固定窗口起点计算
         *
         * <p>例：windowSeconds=60 时 windowStart 为当前分钟的起始秒</p>
         */
        long windowStart = nowSec - (nowSec % windowSeconds);

        /**
         * 计数器更新逻辑
         *
         * <p>当进入新窗口时重置计数为 1 否则在旧计数上加 1</p>
         */
        WindowCounter next = counters.compute(key, (k, old) -> {
            if (old == null || old.windowStartEpochSec != windowStart) {
                return new WindowCounter(windowStart, 1);
            }
            return new WindowCounter(old.windowStartEpochSec, old.count + 1);
        });

        /**
         * 超过阈值则拒绝
         *
         * <p>注意：这里使用 next.count > limit 使得 limit 表示窗口内允许的最大请求数</p>
         */
        if (next.count > limit) {
            String path = exchange.getRequest().getPath().value();
            log.warn("限流触发: IP={}, path={}", key, path);
            exchange.getResponse().setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
            return writer.writeFailure(exchange, 42900, "请求过于频繁，请稍后再试", traceId());
        }

        return chain.filter(exchange);
    }

    /**
     * 过滤器顺序：在 IP 过滤之后执行
     *
     * @return order
     */
    @Override
    public int getOrder() {
        return -90;
    }

    /**
     * 获取客户端 IP（基于 remoteAddress）
     *
     * <p>注意：若服务部署在反向代理后 且需要识别真实 IP 建议改为解析 X-Forwarded-For 等头并配置可信代理</p>
     *
     * @param exchange exchange
     * @return ip
     */
    private static String ip(ServerWebExchange exchange) {
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        if (addr == null || addr.getAddress() == null) {
            return null;
        }
        return addr.getAddress().getHostAddress();
    }

    /**
     * 从 MDC 获取 traceId
     *
     * @return traceId
     */
    private String traceId() {
        return MDC.get(TraceIdHolder.key());
    }
}

