package com.zjl.repository;

import com.zjl.domain.SysDept;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * 部门数据访问层
 */
public interface SysDeptRepository extends JpaRepository<SysDept, Long> {
    /**
     * 按部门名称查询部门
     *
     * @param name 部门名称
     * @return 部门（可能为空）
     */
    Optional<SysDept> findByName(String name);
}

