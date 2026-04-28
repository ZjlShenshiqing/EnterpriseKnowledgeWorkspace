package com.zjl.filter;

import com.zjl.common.trace.TraceIdHolder;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 请求链路追踪过滤器：透传或生成 traceId，并写入 MDC 供日志打印
 */
@Component
public class TraceIdFilter implements WebFilter {

    /**
     * 请求头中的 traceId 键名
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 为每个请求补齐 traceId 并注入 MDC，便于全链路日志追踪
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String incomingTraceId = exchange.getRequest().getHeaders().getFirst(TRACE_ID_HEADER);
        String traceId = StringUtils.hasText(incomingTraceId) ? incomingTraceId : UUID.randomUUID().toString();
        MDC.put(TraceIdHolder.key(), traceId);
        exchange.getResponse().getHeaders().set(TRACE_ID_HEADER, traceId);
        return chain.filter(exchange)
                .doFinally(signalType -> MDC.remove(TraceIdHolder.key()));
    }
}

