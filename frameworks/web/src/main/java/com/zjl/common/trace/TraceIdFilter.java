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
 * 为每个请求注入 traceId 并写入 MDC。
 */
@Component
public class TraceIdFilter extends OncePerRequestFilter {

    /**
     * 请求头中的 traceId 名称
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 过滤器主逻辑。
     */
    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        String incoming = request.getHeader(TRACE_ID_HEADER);
        String traceId = StringUtils.hasText(incoming) ? incoming : UUID.randomUUID().toString();
        MDC.put(TraceIdHolder.key(), traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(TraceIdHolder.key());
        }
    }
}
