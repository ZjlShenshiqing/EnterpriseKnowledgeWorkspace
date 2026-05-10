package com.zjl.knowledge.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * SSE 事件发射器，封装 Spring SseEmitter 的 SSE 输出。
 */
@Slf4j
public class SseEmitter {

    private final org.springframework.web.servlet.mvc.method.annotation.SseEmitter delegate;

    public SseEmitter() {
        this.delegate = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(300_000L);
        this.delegate.onCompletion(() -> log.debug("SSE 连接完成"));
        this.delegate.onTimeout(() -> log.debug("SSE 连接超时"));
        this.delegate.onError(ex -> log.warn("SSE 连接异常", ex));
    }

    /**
     * 获取底层 Spring SseEmitter（供 Controller 返回）。
     *
     * @return Spring SseEmitter
     */
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter getDelegate() {
        return delegate;
    }

    /**
     * 发送命名事件。
     *
     * @param event 事件名
     * @param data  事件数据（将被序列化为 JSON）
     */
    public void send(String event, Object data) {
        try {
            delegate.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                    .event().name(event).data(data));
        } catch (IOException e) {
            log.warn("SSE 发送失败", e);
        }
    }

    /**
     * 发送 message 事件。
     *
     * @param data 消息数据
     */
    public static Map<String, Object> event(String event, Object data) {
        return Map.of("event", event, "data", data);
    }

    /**
     * 标记完成。
     */
    public void complete() {
        try {
            delegate.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                    .event().name("done").data(Map.of()));
            delegate.complete();
        } catch (IOException e) {
            log.warn("SSE 完成发送失败", e);
            delegate.completeWithError(e);
        }
    }

    /**
     * 发送错误。
     *
     * @param message 错误信息
     */
    public void error(String message) {
        try {
            delegate.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                    .event().name("error").data(Map.of("message", message)));
            delegate.complete();
        } catch (IOException e) {
            log.warn("SSE 错误发送失败", e);
            delegate.completeWithError(e);
        }
    }

    /**
     * 发送错误并完成。
     *
     * @param error 异常
     */
    public void completeWithError(Throwable error) {
        error(error.getMessage());
    }
}
