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
        @Index(name = "idx_sys_token_blacklist_token_hash", columnList = "tokenHash", unique = true),
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
     * token 哈希值（SHA-256 hex 64）
     */
    @Column(nullable = false, length = 64)
    private String tokenHash;

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

