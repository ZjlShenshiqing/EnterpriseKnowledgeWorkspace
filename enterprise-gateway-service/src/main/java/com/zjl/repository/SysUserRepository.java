package com.zjl.repository;

import com.zjl.domain.SysUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 用户数据访问层
 */
public interface SysUserRepository extends JpaRepository<SysUser, Long> {
    /**
     * 按用户名查询用户
     *
     * @param username 用户名
     * @return 用户（可能为空）
     */
    Optional<SysUser> findByUsername(String username);
}

