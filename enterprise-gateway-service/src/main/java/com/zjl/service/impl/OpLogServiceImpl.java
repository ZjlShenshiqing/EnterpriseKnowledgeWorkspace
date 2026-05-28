package com.zjl.service.impl;

import com.zjl.domain.SysOpLog;
import com.zjl.repository.SysOpLogRepository;
import com.zjl.service.OpLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OpLogServiceImpl implements OpLogService {

    private final SysOpLogRepository opLogRepository;

    @Override
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
