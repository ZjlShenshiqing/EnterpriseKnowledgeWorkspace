package com.zjl.common.response;

import com.zjl.common.enums.BaseErrorCode;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.AbstractException;
import com.zjl.common.trace.TraceIdHolder;
import org.slf4j.MDC;

import java.util.Optional;

/**
 * 全局返回对象构造器
 */
public final class Results {

    private Results() {
    }

    private static String traceIdFromMdc() {
        return MDC.get(TraceIdHolder.key());
    }

    /**
     * 构造成功响应
     */
    public static Result<Void> success() {
        return new Result<Void>()
                .setCode(Result.SUCCESS_CODE)
                .setMessage("success")
                .setTraceId(traceIdFromMdc());
    }

    /**
     * 构造带返回数据的成功响应
     */
    public static <T> Result<T> success(T data) {
        return new Result<T>()
                .setCode(Result.SUCCESS_CODE)
                .setMessage("success")
                .setData(data)
                .setTraceId(traceIdFromMdc());
    }

    /**
     * 构建服务端失败响应（使用 MDC 中的 traceId）
     */
    public static Result<Void> failure() {
        return new Result<Void>()
                .setCode(BaseErrorCode.SERVICE_ERROR.code())
                .setMessage(BaseErrorCode.SERVICE_ERROR.message())
                .setTraceId(traceIdFromMdc());
    }

    /**
     * 通过 {@link AbstractException} 构建失败响应
     */
    public static Result<Void> failure(AbstractException abstractException) {
        String errorCode = Optional.ofNullable(abstractException.getErrorCode())
                .orElse(BaseErrorCode.SERVICE_ERROR.code());
        String errorMessage = Optional.ofNullable(abstractException.getMessage())
                .orElse(BaseErrorCode.SERVICE_ERROR.message());
        return new Result<Void>()
                .setCode(errorCode)
                .setMessage(errorMessage)
                .setTraceId(traceIdFromMdc());
    }

    /**
     * 通过 errorCode，errorMessage 构建失败响应（使用 MDC 中的 traceId）
     */
    public static Result<Void> failure(String errorCode, String errorMessage) {
        return new Result<Void>()
                .setCode(errorCode)
                .setMessage(errorMessage)
                .setTraceId(traceIdFromMdc());
    }

    /**
     * 通过数值错误码构建失败响应，并显式指定 traceId（适用于 WebFlux 等需透传的场景）。
     */
    public static Result<Void> failure(int numericCode, String errorMessage, String traceId) {
        return new Result<Void>()
                .setCode(String.valueOf(numericCode))
                .setMessage(errorMessage)
                .setTraceId(traceId);
    }

    /**
     * 通过 {@link ErrorCode} 构建失败响应，并显式指定 traceId。
     */
    public static Result<Void> failure(ErrorCode errorCode, String traceId) {
        return failure(errorCode.getCode(), errorCode.getMessage(), traceId);
    }
}
