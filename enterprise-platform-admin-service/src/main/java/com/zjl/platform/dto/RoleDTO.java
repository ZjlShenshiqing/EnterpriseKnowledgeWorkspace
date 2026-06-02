package com.zjl.platform.dto;

import com.zjl.platform.entity.SysPermission;
import com.zjl.platform.entity.SysRole;

import java.time.LocalDateTime;
import java.util.Set;

public record RoleDTO(
        Long id,
        String code,
        String name,
        Set<SysPermission> permissions,
        long userCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static RoleDTO from(SysRole role, Set<SysPermission> permissions, long userCount) {
        return new RoleDTO(
                role.getId(),
                role.getCode(),
                role.getName(),
                permissions,
                userCount,
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }
}
