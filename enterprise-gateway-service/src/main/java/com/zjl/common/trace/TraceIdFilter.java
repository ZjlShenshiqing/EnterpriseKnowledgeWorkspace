package com.zjl.common.trace;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 请求链路追踪过滤器：透传或生成 traceId，并写入 MDC 供日志打印。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    /**
     * 请求头中的 traceId 键名
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 为每个请求补齐 traceId 并注入 MDC，便于全链路日志追踪。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incomingTraceId = request.getHeader(TRACE_ID_HEADER);
        String traceId = StringUtils.hasText(incomingTraceId) ? incomingTraceId : UUID.randomUUID().toString();
        MDC.put(TraceIdHolder.key(), traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIdHolder.key());
        }
    }
}
