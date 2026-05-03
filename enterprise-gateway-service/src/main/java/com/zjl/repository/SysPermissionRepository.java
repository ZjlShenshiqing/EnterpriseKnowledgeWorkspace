package com.zjl.repository;

import com.zjl.domain.SysPermission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 权限数据访问层
 */
public interface SysPermissionRepository extends JpaRepository<SysPermission, Long> {
    /**
     * 按权限编码查询权限
     *
     * @param code 权限编码
     * @return 权限（可能为空）
     */
    Optional<SysPermission> findByCode(String code);
}

