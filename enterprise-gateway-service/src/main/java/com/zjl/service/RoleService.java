package com.zjl.service;

import com.zjl.common.exception.BizException;
import com.zjl.domain.SysPermission;
import com.zjl.domain.SysRole;
import com.zjl.dto.RoleDTO;
import com.zjl.repository.SysPermissionRepository;
import com.zjl.repository.SysRoleRepository;
import com.zjl.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理业务逻辑
 *
 * <p>封装 JPA 阻塞调用，供 Controller 通过 Mono.fromCallable 在弹性线程池中调用。</p>
 */
@Service
@Transactional
public class RoleService {

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final SysUserRepository userRepository;

    public RoleService(
            SysRoleRepository roleRepository,
            SysPermissionRepository permissionRepository,
            SysUserRepository userRepository
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
    }

    /**
     * 角色列表（附带 userCount）
     *
     * @return 角色 DTO 列表
     */
    public List<RoleDTO> listRoles() {
        List<SysRole> roles = roleRepository.findAll();
        return roles.stream()
                .map(r -> RoleDTO.from(r, userRepository.countByRoleId(r.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 按 ID 查询角色
     *
     * @param id 角色 ID
     * @return 角色实体
     * @throws BizException(40400) 角色不存在
     */
    public SysRole getRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "角色不存在"));
    }

    /**
     * 创建角色
     *
     * @param code 角色编码
     * @param name 角色名称
     * @param permissionCodes 权限编码集合（可选）
     * @return 创建后的角色
     * @throws BizException(40000) 角色编码已存在
     */
    public SysRole createRole(String code, String name, Set<String> permissionCodes) {
        if (roleRepository.findByCode(code).isPresent()) {
            throw new BizException(40000, "角色 code 已存在");
        }
        SysRole r = new SysRole();
        r.setCode(code);
        r.setName(name);
        if (permissionCodes != null && !permissionCodes.isEmpty()) {
            Set<SysPermission> perms = resolvePermissions(permissionCodes);
            r.setPermissions(perms);
        }
        return roleRepository.save(r);
    }

    /**
     * 更新角色
     *
     * @param id 角色 ID
     * @param name 角色名称（null 则不更新）
     * @param permissionCodes 权限编码集合（null 则不更新）
     * @return 更新后的角色
     */
    public SysRole updateRole(Long id, String name, Set<String> permissionCodes) {
        SysRole r = roleRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "角色不存在"));
        if (name != null) r.setName(name);
        if (permissionCodes != null) {
            Set<SysPermission> perms = resolvePermissions(permissionCodes);
            r.setPermissions(perms);
        }
        return roleRepository.save(r);
    }

    /**
     * 删除角色
     *
     * @param id 角色 ID
     * @throws BizException(40000) 角色下存在用户
     */
    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new BizException(40400, "角色不存在");
        }
        long userCount = userRepository.countByRoleId(id);
        if (userCount > 0) {
            throw new BizException(40000, "角色下存在用户，无法删除");
        }
        roleRepository.deleteById(id);
    }

    /**
     * 将权限编码集合转为 SysPermission 实体集合
     */
    private Set<SysPermission> resolvePermissions(Set<String> permissionCodes) {
        return permissionCodes.stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseThrow(() -> new BizException(40000, "权限不存在: " + code)))
                .collect(Collectors.toSet());
    }
}
