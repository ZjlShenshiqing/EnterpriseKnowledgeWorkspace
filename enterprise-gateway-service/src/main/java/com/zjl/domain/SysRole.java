package com.zjl.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * 系统角色实体
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role", indexes = {
        @Index(name = "idx_sys_role_code", columnList = "code", unique = true)
})
public class SysRole {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 角色编码（唯一）
     */
    @Column(nullable = false, length = 64)
    private String code;

    /**
     * 角色名称
     */
    @Column(nullable = false, length = 128)
    private String name;

    /**
     * 角色权限集合
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "sys_role_permission",
            joinColumns = @JoinColumn(name = "role_id"),
            inverseJoinColumns = @JoinColumn(name = "permission_id")
    )
    private Set<SysPermission> permissions = new HashSet<>();

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

