package com.zjl.common.trace;

/**
 * traceId 常量持有类，统一维护 MDC 中的键名，避免散落硬编码。
 */
public final class TraceIdHolder {

    /**
     * MDC 中 traceId 的统一键名
     */
    private static final String TRACE_ID_KEY = "traceId";

    private TraceIdHolder() {
    }

    /**
     * 返回 MDC 键名。
     *
     * @return traceId 键
     */
    public static String key() {
        return TRACE_ID_KEY;
    }
}
