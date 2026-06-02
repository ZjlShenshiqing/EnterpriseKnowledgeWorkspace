package com.zjl.collaboration.integration;

import java.util.List;

/**
 * 用户简要信息，从网关 HTTP 接口获取。
 */
public record UserInfo(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName,
        List<RoleInfo> roles
) {
    public UserInfo(Long userId, String username, String realName, Long deptId, String deptName) {
        this(userId, username, realName, deptId, deptName, List.of());
    }

    public record RoleInfo(Long id, String code, String name) {
    }
}
