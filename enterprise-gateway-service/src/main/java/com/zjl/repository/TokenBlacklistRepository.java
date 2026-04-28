package com.zjl.repository;

import com.zjl.domain.TokenBlacklistEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Token 黑名单数据访问层
 */
public interface TokenBlacklistRepository extends JpaRepository<TokenBlacklistEntry, Long> {
    /**
     * 按 token 查询黑名单条目
     *
     * @param token JWT 原文
     * @return 黑名单条目（可能为空）
     */
    Optional<TokenBlacklistEntry> findByToken(String token);

    /**
     * 删除过期的黑名单条目
     *
     * @param now 当前时间
     * @return 删除条数
     */
    long deleteByExpiresAtBefore(Instant now);
}

