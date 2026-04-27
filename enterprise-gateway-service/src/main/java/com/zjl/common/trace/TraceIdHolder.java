package com.zjl.common.trace;

public final class TraceIdHolder {

    private static final String TRACE_ID_KEY = "traceId";

    private TraceIdHolder() {
    }

    public static String key() {
        return TRACE_ID_KEY;
    }
}
