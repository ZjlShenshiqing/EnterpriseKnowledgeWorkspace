package com.zjl.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 系统权限实体
 */
@Getter
@Setter
@Entity
@Table(name = "sys_permission", indexes = {
        @Index(name = "idx_sys_permission_code", columnList = "code", unique = true)
})
public class SysPermission {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 权限编码（建议采用 domain:action 形式，如 system:user:read）
     */
    @Column(nullable = false, length = 128)
    private String code;

    /**
     * 权限名称
     */
    @Column(nullable = false, length = 200)
    private String name;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * 更新时间
     */
    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}

