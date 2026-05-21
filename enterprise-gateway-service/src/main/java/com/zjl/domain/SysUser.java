package com.zjl.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * 系统用户实体
 */
@Getter
@Setter
@Entity
@Table(name = "sys_user", indexes = {
        @Index(name = "idx_sys_user_username", columnList = "username", unique = true)
})
public class SysUser {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 用户名（唯一）
     */
    @Column(nullable = false, length = 64)
    private String username;

    /**
     * 密码哈希（BCrypt）
     */
    @JsonIgnore
    @Column(nullable = false, length = 200)
    private String passwordHash;

    /**
     * 真实姓名
     */
    @Column(length = 64)
    private String realName;

    /**
     * 所属部门
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dept_id")
    private SysDept dept;

    /**
     * 是否启用
     */
    @Column(nullable = false)
    private boolean enabled = true;

    /**
     * 用户角色集合
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "sys_user_role",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id")
    )
    private Set<SysRole> roles = new HashSet<>();

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

