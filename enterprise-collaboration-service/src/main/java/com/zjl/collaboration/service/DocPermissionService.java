package com.zjl.collaboration.service;

/**
 * 文档权限服务
 */
public interface DocPermissionService {

    enum Permission { VIEW, COMMENT, EDIT }

    Permission checkPermission(Long docId, Long userId, Long deptId);

    Permission checkShareToken(String token);
}
