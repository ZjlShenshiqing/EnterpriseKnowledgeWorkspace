package com.zjl.service;

import com.zjl.domain.SysOpLog;
import com.zjl.repository.SysOpLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

/**
 * 操作日志服务
 *
 * <p>异步记录用户操作行为，通过 {@code Schedulers.boundedElastic()} 将写库操作提交到独立线程池，
 * 不阻塞主链路</p>
 */
@Service
@RequiredArgsConstructor
public class OpLogService {

    private final SysOpLogRepository opLogRepository;

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
    public Mono<Void> log(Long userId, String username, String action, Object request, String detail) {
        String method = "";
        String path = "";
        if (request instanceof ServerHttpRequest req) {
            method = req.getMethod() != null ? req.getMethod().name() : "";
            path = req.getPath().value();
        }
        final String finalMethod = method;
        final String finalPath = path;
        return Mono.fromRunnable(() -> {
                    SysOpLog log = new SysOpLog();
                    log.setUserId(userId);
                    log.setUsername(username != null ? username : "");
                    log.setAction(action);
                    log.setMethod(finalMethod);
                    log.setPath(finalPath);
                    log.setDetail(detail);
                    log.setCreatedAt(Instant.now());
                    opLogRepository.save(log);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
