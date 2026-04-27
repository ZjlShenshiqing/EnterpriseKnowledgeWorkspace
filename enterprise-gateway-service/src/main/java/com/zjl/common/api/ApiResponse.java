package com.zjl.common.api;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApiResponse<T> {

    private final int code;
    private final String message;
    private final T data;
    private final String traceId;

    public static <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .traceId(traceId)
                .build();
    }

    public static ApiResponse<Void> success(String traceId) {
        return success(null, traceId);
    }

    public static ApiResponse<Void> failure(int code, String message, String traceId) {
        return ApiResponse.<Void>builder()
                .code(code)
                .message(message)
                .data(null)
                .traceId(traceId)
                .build();
    }
}
