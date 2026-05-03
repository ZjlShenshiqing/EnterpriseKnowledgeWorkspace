package com.zjl.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

/**
 * 系统关键操作审计日志实体
 */
@Getter
@Setter
@Entity
@Table(name = "sys_op_log", indexes = {
        @Index(name = "idx_sys_op_log_user_id", columnList = "userId"),
        @Index(name = "idx_sys_op_log_created_at", columnList = "createdAt")
})
public class SysOpLog {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 操作用户 id（可能为空，如匿名/系统动作）
     */
    @Column
    private Long userId;

    /**
     * 操作用户名（为空时保存空串）
     */
    @Column(nullable = false, length = 64)
    private String username;

    /**
     * 操作类型（如 CREATE_USER、CREATE_ROLE）
     */
    @Column(nullable = false, length = 32)
    private String action;

    /**
     * HTTP 方法
     */
    @Column(nullable = false, length = 32)
    private String method;

    /**
     * 请求路径
     */
    @Column(nullable = false, length = 512)
    private String path;

    /**
     * 操作详情（可选）
     */
    @Column(length = 2000)
    private String detail;

    /**
     * 创建时间
     */
    @Column(nullable = false)
    private Instant createdAt = Instant.now();
}

