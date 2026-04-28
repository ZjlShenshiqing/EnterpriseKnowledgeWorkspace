package com.zjl.common.api;

import lombok.Builder;
import lombok.Getter;

/**
 * 统一接口响应结构，匹配项目约定的 code/message/data/traceId 格式。
 *
 * @param <T> 业务数据类型
 */
@Getter
@Builder
public class ApiResponse<T> {

    /**
     * 业务状态码
     */
    private final int code;

    /**
     * 响应消息
     */
    private final String message;

    /**
     * 业务数据
     */
    private final T data;

    /**
     * 链路追踪标识
     */
    private final String traceId;

    /**
     * 构建成功响应
     *
     * @param data 业务数据
     * @param traceId 链路追踪标识
     * @return 标准成功响应
     * @param <T> 业务数据类型
     */
    public static <T> ApiResponse<T> success(T data, String traceId) {
        return ApiResponse.<T>builder()
                .code(200)
                .message("success")
                .data(data)
                .traceId(traceId)
                .build();
    }

    /**
     * 构建无业务数据的成功响应。
     *
     * @param traceId 链路追踪标识
     * @return 标准成功响应
     */
    public static ApiResponse<Void> success(String traceId) {
        return success(null, traceId);
    }

    /**
     * 构建失败响应。
     *
     * @param code 业务状态码
     * @param message 错误消息
     * @param traceId 链路追踪标识
     * @return 标准失败响应
     */
    public static ApiResponse<Void> failure(int code, String message, String traceId) {
        return ApiResponse.<Void>builder()
                .code(code)
                .message(message)
                .data(null)
                .traceId(traceId)
                .build();
    }
}
