package com.zjl.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 系统角色实体（网关仅用于登录鉴权，不含权限管理）。
 */
@Getter
@Setter
@Entity
@Table(name = "sys_role", indexes = {
        @Index(name = "idx_sys_role_code", columnList = "code", unique = true)
})
public class SysRole {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String code;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    @Column(nullable = false)
    private Instant updatedAt = Instant.now();
}

