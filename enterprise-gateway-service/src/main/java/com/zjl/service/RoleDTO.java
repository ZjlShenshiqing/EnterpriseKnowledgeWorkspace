package com.zjl.service;

import com.zjl.domain.SysPermission;
import com.zjl.domain.SysRole;

import java.time.Instant;
import java.util.Set;

/**
 * 角色数据传输对象，附带关联用户数统计
 *
 * @param id 角色 ID
 * @param code 角色编码
 * @param name 角色名称
 * @param permissions 权限集合
 * @param userCount 拥有该角色的用户数
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record RoleDTO(
        Long id,
        String code,
        String name,
        Set<SysPermission> permissions,
        long userCount,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * 从 SysRole 实体转换
     *
     * @param role 角色实体
     * @param userCount 关联用户数
     * @return RoleDTO
     */
    public static RoleDTO from(SysRole role, long userCount) {
        return new RoleDTO(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getPermissions(),
                userCount,
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }
}
