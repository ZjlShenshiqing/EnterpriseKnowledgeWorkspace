package com.zjl.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * JWT Token 黑名单条目实体
 */
@Getter
@Setter
@Entity
@Table(name = "sys_token_blacklist", indexes = {
        @Index(name = "idx_sys_token_blacklist_token", columnList = "token", unique = true),
        @Index(name = "idx_sys_token_blacklist_expires_at", columnList = "expiresAt")
})
public class TokenBlacklistEntry {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * JWT 原文
     */
    @Column(nullable = false, length = 2048)
    private String token;

    /**
     * token 过期时间（用于自动清理）
     */
    @Column(nullable = false)
    private Instant expiresAt;

    /**
     * 拉黑时间
     */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}

