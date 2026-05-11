package com.zjl.knowledge.web;

import lombok.Builder;
import lombok.Getter;

/**
 * 当前请求用户上下文（由网关透传请求头模拟，一期本地联调使用）
 */
@Getter
@Builder
public class UserContext {

    /**
     * 用户 ID
     */
    private final Long userId;

    /**
     * 部门 ID
     */
    private final Long departmentId;

    /**
     * 项目 ID（可选，用于 PROJECT 权限）
     */
    private final Long projectId;

    /**
     * 是否管理员
     */
    private final boolean admin;
}
