package com.zjl.common.exception;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 全局异常处理器，将异常统一映射为 {@link Result}。
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
    public Result<Void> handleBizException(BizException ex) {
        return Results.failure(ex);
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
    public Result<Void> handleValidationException(Exception ex) {
        return Results.failure(String.valueOf(ErrorCode.PARAM_INVALID.getCode()), ErrorCode.PARAM_INVALID.getMessage());
    }

    /**
     * 处理未捕获异常。
     *
     * @param ex 未知异常
     * @return 标准失败响应
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleUnknownException(Exception ex) {
        return Results.failure(String.valueOf(ErrorCode.SYSTEM_ERROR.getCode()), ErrorCode.SYSTEM_ERROR.getMessage());
    }
}
