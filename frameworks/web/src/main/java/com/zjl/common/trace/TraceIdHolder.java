package com.zjl.common.trace;

/**
 * traceId 在 MDC 中的键名常量。
 */
public final class TraceIdHolder {

    /**
     * MDC 键名
     */
    private static final String TRACE_ID_KEY = "traceId";

    private TraceIdHolder() {
    }

    /**
     * 返回 MDC 键名。
     *
     * @return 键名
     */
    public static String key() {
        return TRACE_ID_KEY;
    }
}
