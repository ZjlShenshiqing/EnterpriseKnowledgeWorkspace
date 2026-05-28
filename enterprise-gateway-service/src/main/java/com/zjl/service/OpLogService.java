package com.zjl.service;

import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

/**
 * 操作日志服务
 *
 * <p>异步记录用户操作行为，不阻塞主链路。</p>
 */
public interface OpLogService {

    /**
     * 异步写入一条操作日志
     *
     * @param userId   操作人 ID
     * @param username 操作人用户名
     * @param action   操作类型（如 login、create_doc、delete_meeting）
     * @param request  原始请求（提取 method 和 path）
     * @param detail   操作详情描述
     * @return Mono<Void> 不阻塞主链路
     */
    Mono<Void> log(Long userId, String username, String action, Object request, String detail);
}
