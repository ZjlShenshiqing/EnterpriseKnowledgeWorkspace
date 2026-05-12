package com.zjl.service;

import com.zjl.domain.SysOpLog;
import com.zjl.repository.SysOpLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class OpLogService {

    private final SysOpLogRepository opLogRepository;

    public Mono<Void> log(Long userId, String username, String action, Object request, String detail) {
        return Mono.fromRunnable(() -> {
            SysOpLog log = new SysOpLog();
            log.setUserId(userId);
            log.setUsername(username != null ? username : "");
            log.setAction(action);
            log.setMethod("");
            log.setPath("");
            log.setDetail(detail);
            log.setCreatedAt(Instant.now());
            opLogRepository.save(log);
        }).subscribeOn(Schedulers.boundedElastic()).then();
    }
}
