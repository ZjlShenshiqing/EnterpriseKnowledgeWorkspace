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
     * 按 token 哈希查询黑名单条目
     *
     * @param tokenHash token 哈希
     * @return 黑名单条目（可能为空）
     */
    Optional<TokenBlacklistEntry> findByTokenHash(String tokenHash);

    /**
     * 删除过期的黑名单条目
     *
     * @param now 当前时间
     * @return 删除条数
     */
    long deleteByExpiresAtBefore(Instant now);
}

