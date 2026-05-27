package com.zjl.collaboration.integration;

/**
 * 用户简要信息，从网关 HTTP 接口获取。
 */
public record UserInfo(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName
) {}
