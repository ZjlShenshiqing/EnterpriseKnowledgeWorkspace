package com.zjl.common.response;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * 统一 JSON 响应输出器（WebFlux 过滤器链等场景）。
 */
@Component
public class ApiResponseWriter {

    private final ObjectMapper objectMapper;

    public ApiResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 写入统一响应
     *
     * @param exchange exchange
     * @param body 响应体
     * @return 完成信号
     */
    public Mono<Void> write(ServerWebExchange exchange, Result<?> body) {
        return writeRaw(exchange, body);
    }

    /**
     * 写入失败响应
     *
     * @param exchange exchange
     * @param code 错误码（数值，序列化为字符串）
     * @param message 错误消息
     * @param traceId traceId
     * @return 完成信号
     */
    public Mono<Void> writeFailure(ServerWebExchange exchange, int code, String message, String traceId) {
        return writeRaw(exchange, Results.failure(code, message, traceId));
    }

    /**
     * 写入任意对象为 JSON
     *
     * @param exchange exchange
     * @param body body
     * @return 完成信号
     */
    public Mono<Void> writeRaw(ServerWebExchange exchange, Object body) {
        byte[] bytes;
        try {
            bytes = objectMapper.writeValueAsBytes(body);
        } catch (JsonProcessingException e) {
            bytes = "{\"code\":\"50000\",\"message\":\"系统异常\",\"data\":null,\"traceId\":null}"
                    .getBytes(StandardCharsets.UTF_8);
        }
        exchange.getResponse().getHeaders().setContentType(MediaType.APPLICATION_JSON);
        return exchange.getResponse().writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
    }
}
