package com.zjl.service;

import com.zjl.domain.SysRole;
import com.zjl.dto.RoleDTO;

import java.util.List;
import java.util.Set;

/**
 * 角色管理业务逻辑
 *
 * <p>封装 JPA 阻塞调用，供 Controller 通过 Mono.fromCallable 在弹性线程池中调用。</p>
 */
public interface RoleService {

    /**
     * 角色列表（附带 userCount）
     *
     * @return 角色 DTO 列表
     */
    List<RoleDTO> listRoles();

    /**
     * 按 ID 查询角色
     *
     * @param id 角色 ID
     * @return 角色实体
     * @throws com.zjl.common.exception.BizException(40400) 角色不存在
     */
    SysRole getRole(Long id);

    /**
     * 创建角色
     *
     * @param code 角色编码
     * @param name 角色名称
     * @param permissionCodes 权限编码集合（可选）
     * @return 创建后的角色
     * @throws com.zjl.common.exception.BizException(40000) 角色编码已存在
     */
    SysRole createRole(String code, String name, Set<String> permissionCodes);

    /**
     * 更新角色
     *
     * @param id 角色 ID
     * @param name 角色名称（null 则不更新）
     * @param permissionCodes 权限编码集合（null 则不更新）
     * @return 更新后的角色
     */
    SysRole updateRole(Long id, String name, Set<String> permissionCodes);

    /**
     * 删除角色
     *
     * @param id 角色 ID
     * @throws com.zjl.common.exception.BizException(40000) 角色下存在用户
     */
    void deleteRole(Long id);
}
