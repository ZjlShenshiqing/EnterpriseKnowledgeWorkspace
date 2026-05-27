package com.zjl.service;

import com.zjl.domain.SysUser;

/**
 * 用户简要信息，供下游服务通过 HTTP 批量查询。
 *
 * @param userId   用户 ID
 * @param username 用户名
 * @param realName 姓名
 * @param deptId   部门 ID
 * @param deptName 部门名称
 */
public record UserInfoDTO(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName
) {
    /**
     * 从 SysUser 实体转换。
     */
    public static UserInfoDTO from(SysUser user) {
        return new UserInfoDTO(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDept() != null ? user.getDept().getId() : null,
                user.getDept() != null ? user.getDept().getName() : null
        );
    }
}
