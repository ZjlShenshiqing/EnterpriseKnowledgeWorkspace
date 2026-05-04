package com.zjl.knowledge.domain;

/**
 * 文档权限类型
 */
public enum DocumentPermissionType {

    /**
     * 全员可见
     */
    ALL,

    /**
     * 部门可见
     */
    DEPARTMENT,

    /**
     * 项目成员可见（需配合 kb_document_permission）
     */
    PROJECT,

    /**
     * 指定人员可见（需配合 kb_document_permission）
     */
    USER,

    /**
     * 管理员可见
     */
    ADMIN
}
