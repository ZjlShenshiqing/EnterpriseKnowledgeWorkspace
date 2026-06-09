package com.zjl.knowledge.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 事件发射器，封装 Spring SseEmitter 的 SSE 输出
 */
@Slf4j
public class AgentSseEmitter {

    private final org.springframework.web.servlet.mvc.method.annotation.SseEmitter delegate;
    
    /**
     * 连接状态标记：true 表示连接已断开（完成、超时、错误）
     */
    private final AtomicBoolean disconnected = new AtomicBoolean(false);

    public AgentSseEmitter() {
        this.delegate = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(300_000L);
        this.delegate.onCompletion(() -> {
            disconnected.set(true);
            log.debug("SSE 连接完成");
        });
        this.delegate.onTimeout(() -> {
            disconnected.set(true);
            log.debug("SSE 连接超时");
        });
        this.delegate.onError(ex -> {
            disconnected.set(true);
            log.warn("SSE 连接异常", ex);
        });
    }

    /**
     * 获取底层 Spring SseEmitter（供 Controller 返回）
     *
     * @return Spring SseEmitter
     */
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter getDelegate() {
        return delegate;
    }

    /**
     * 检查连接是否已断开
     *
     * @return true 如果连接已断开
     */
    public boolean isDisconnected() {
        return disconnected.get();
    }

    /**
     * 发送命名事件
     *
     * @param event 事件名
     * @param data  事件数据（将被序列化为 JSON）
     * @throws IllegalStateException 如果连接已断开
     */
    public void send(String event, Object data) {
        if (disconnected.get()) {
            throw new IllegalStateException("SSE 连接已断开，无法发送消息");
        }
        try {
            delegate.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                    .event().name(event).data(data));
        } catch (IOException e) {
            disconnected.set(true);
            throw new IllegalStateException("SSE 发送失败，连接可能已断开", e);
        }
    }

    /**
     * 发送 message 事件
     *
     * @param data 消息数据
     */
    public static Map<String, Object> event(String event, Object data) {
        return Map.of("event", event, "data", data);
    }

    /**
     * 标记完成
     */
    public void complete() {
        if (disconnected.get()) {
            log.debug("SSE 连接已断开，跳过 complete");
            return;
        }
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
     * 发送错误
     *
     * @param message 错误信息
     */
    public void error(String message) {
        if (disconnected.get()) {
            log.debug("SSE 连接已断开，跳过 error: {}", message);
            return;
        }
        try {
            delegate.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter
                    .event().name("error").data(Map.of("message", message != null ? message : "未知错误")));
            delegate.complete();
        } catch (IOException e) {
            log.warn("SSE 错误发送失败", e);
            delegate.completeWithError(e);
        }
    }

    /**
     * 发送错误并完成
     *
     * @param error 异常
     */
    public void completeWithError(Throwable error) {
        error(error.getMessage());
    }
}
