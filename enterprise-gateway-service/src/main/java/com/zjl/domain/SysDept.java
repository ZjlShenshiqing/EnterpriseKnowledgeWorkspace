package com.zjl.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.SQLRestriction;

import java.time.Instant;

/**
 * 部门实体
 */
@Getter
@Setter
@Entity
@SQLRestriction("deleted = 0")
@Table(name = "sys_dept", indexes = {
        @Index(name = "idx_sys_dept_name", columnList = "name", unique = true)
})
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class SysDept {

    /**
     * 主键
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 部门名称（唯一）
     */
    @Column(nullable = false, length = 128)
    private String name;

    /**
     * 父部门 id（根部门可为空）
     */
    @Column
    private Long parentId;

    /**
     * 逻辑删除（0=正常，1=已删除）
     */
    @Column(nullable = false)
    private Integer deleted = 0;

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

