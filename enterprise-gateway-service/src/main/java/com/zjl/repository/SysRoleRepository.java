package com.zjl.repository;

import com.zjl.domain.SysRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 角色数据访问层
 */
public interface SysRoleRepository extends JpaRepository<SysRole, Long> {
    /**
     * 按角色编码查询角色
     *
     * @param code 角色编码
     * @return 角色（可能为空）
     */
    Optional<SysRole> findByCode(String code);
}

