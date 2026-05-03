package com.zjl.common.exception;

import com.zjl.common.api.ApiResponse;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.trace.TraceIdHolder;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，将异常统一映射为 ApiResponse。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 处理业务异常。
     *
     * @param ex 业务异常
     * @return 标准失败响应
     */
    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException ex) {
        return ApiResponse.failure(ex.getCode(), ex.getMessage(), traceId());
    }

    /**
     * 处理参数校验相关异常。
     *
     * @param ex 校验异常
     * @return 标准失败响应
     */
    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ApiResponse<Void> handleValidationException(Exception ex) {
        return ApiResponse.failure(ErrorCode.PARAM_INVALID.getCode(), ErrorCode.PARAM_INVALID.getMessage(), traceId());
    }

    /**
     * 处理未捕获异常。
     *
     * @param ex 未知异常
     * @return 标准失败响应
     */
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknownException(Exception ex) {
        return ApiResponse.failure(ErrorCode.SYSTEM_ERROR.getCode(), ErrorCode.SYSTEM_ERROR.getMessage(), traceId());
    }

    /**
     * 从 MDC 中读取 traceId。
     *
     * @return 当前请求 traceId
     */
    private String traceId() {
        return MDC.get(TraceIdHolder.key());
    }
}
