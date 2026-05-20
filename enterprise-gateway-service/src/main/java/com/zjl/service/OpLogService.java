package com.zjl.service;

import com.zjl.domain.SysOpLog;
import com.zjl.repository.SysOpLogRepository;
import lombok.RequiredArgsConstructor;
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
     * @param request  原始请求体（预留，当前未序列化）
     * @param detail   操作详情描述
     * @return Mono<Void> 不阻塞主链路
     */
    public Mono<Void> log(Long userId, String username, String action, Object request, String detail) {
        return Mono.fromRunnable(() -> {
                    // 构建日志实体
                    SysOpLog log = new SysOpLog();
                    log.setUserId(userId);
                    log.setUsername(username != null ? username : "");
                    log.setAction(action);
                    // method 和 path 预留字段，当前由上层过滤器统一填充
                    log.setMethod("");
                    log.setPath("");
                    log.setDetail(detail);
                    log.setCreatedAt(Instant.now());
                    // 写入数据库
                    opLogRepository.save(log);
                })
                // 提交到弹性线程池执行，不阻塞 Netty IO 线程
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }
}
