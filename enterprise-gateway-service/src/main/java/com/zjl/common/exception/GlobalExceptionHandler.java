package com.zjl.common.exception;

import com.zjl.common.api.ApiResponse;
import com.zjl.common.trace.TraceIdHolder;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.MDC;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public ApiResponse<Void> handleBizException(BizException ex) {
        return ApiResponse.failure(ex.getCode(), ex.getMessage(), traceId());
    }

    @ExceptionHandler({
            MethodArgumentNotValidException.class,
            BindException.class,
            ConstraintViolationException.class,
            HttpMessageNotReadableException.class
    })
    public ApiResponse<Void> handleValidationException(Exception ex) {
        return ApiResponse.failure(400, "请求参数不合法", traceId());
    }

    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleUnknownException(Exception ex) {
        return ApiResponse.failure(500, "系统异常", traceId());
    }

    private String traceId() {
        return MDC.get(TraceIdHolder.key());
    }
}
