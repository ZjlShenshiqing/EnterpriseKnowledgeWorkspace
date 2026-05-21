package com.zjl.security;

import com.zjl.domain.TokenBlacklistEntry;
import com.zjl.repository.TokenBlacklistRepository;
import io.jsonwebtoken.Claims;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;

/**
 * Token 黑名单服务：用于退出后让已签发的 JWT 立即失效
 */
@Service
public class TokenBlacklistService {

    /**
     * 黑名单仓库
     */
    private final TokenBlacklistRepository repository;
    /**
     * JWT 服务（用于读取 token 过期时间）
     */
    private final JwtUtil jwtService;

    /**
     * 构造器注入
     *
     * @param repository 黑名单仓库
     * @param jwtService JWT 服务
     */
    public TokenBlacklistService(TokenBlacklistRepository repository, JwtUtil jwtService) {
        this.repository = repository;
        this.jwtService = jwtService;
    }

    /**
     * 判断 token 是否在黑名单中
     *
     * @param token JWT 原文
     * @return 是否拉黑
     */
    public Mono<Boolean> isBlacklisted(String token) {
        return Mono.fromCallable(() -> repository.findByTokenHash(sha256Hex(token)).isPresent())
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorReturn(false);
    }

    /**
     * 将 token 拉黑，记录其过期时间以便后续清理
     *
     * @param token JWT 原文
     * @return 完成信号
     */
    public Mono<Void> blacklist(String token) {
        return Mono.fromCallable(() -> {
                    Claims claims = jwtService.parse(token);
                    Date exp = claims.getExpiration();
                    TokenBlacklistEntry entry = new TokenBlacklistEntry();
                    entry.setTokenHash(sha256Hex(token));
                    entry.setExpiresAt(exp.toInstant());
                    repository.save(entry);
                    return 1;
                })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    /**
     * 定时清理已过期的黑名单条目
     */
    @Scheduled(fixedDelay = 60_000)
    public void cleanup() {
        repository.deleteByExpiresAtBefore(Instant.now());
    }

    /**
     * 计算 SHA-256 hex
     *
     * @param raw 原文
     * @return hex
     */
    private static String sha256Hex(String raw) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 failed");
        }
    }
}

