package com.zjl.platform.dto;

import com.zjl.platform.entity.SysUser;

public record UserInfoDTO(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName
) {
    public static UserInfoDTO from(SysUser user, String deptName) {
        return new UserInfoDTO(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDeptId(),
                deptName
        );
    }
}
