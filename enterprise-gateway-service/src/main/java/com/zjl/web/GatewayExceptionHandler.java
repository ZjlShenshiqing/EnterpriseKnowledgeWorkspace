package com.zjl.web;

import com.zjl.common.exception.BizException;
import com.zjl.common.response.Results;
import com.zjl.common.trace.TraceIdHolder;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

/**
 * 网关 WebFlux 全局异常处理，将业务异常转为统一 {@link com.zjl.common.response.Result}。
 */
@Slf4j
@RestControllerAdvice
@Order(-1)
public class GatewayExceptionHandler {

    /**
     * 业务异常
     */
    @ExceptionHandler(BizException.class)
    @ResponseStatus(HttpStatus.OK)
    public Mono<com.zjl.common.response.Result<Void>> handleBiz(BizException ex) {
        log.error("网关路由异常: error={}", ex.getMessage(), ex);
        return Mono.just(Results.failure(ex.getCode(), ex.getMessage(), traceId()));
    }

    /**
     * 参数校验失败
     */
    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.OK)
    public Mono<com.zjl.common.response.Result<Void>> handleValidation(WebExchangeBindException ex) {
        String msg = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .orElse("参数校验失败");
        log.warn("网关参数校验失败: error={}", msg);
        return Mono.just(Results.failure(40000, msg, traceId()));
    }

    private static String traceId() {
        return MDC.get(TraceIdHolder.key());
    }
}
