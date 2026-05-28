package com.zjl.service.impl;

import com.zjl.common.exception.BizException;
import com.zjl.domain.SysPermission;
import com.zjl.domain.SysRole;
import com.zjl.dto.RoleDTO;
import com.zjl.repository.SysPermissionRepository;
import com.zjl.repository.SysRoleRepository;
import com.zjl.repository.SysUserRepository;
import com.zjl.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleServiceImpl implements RoleService {

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final SysUserRepository userRepository;

    public RoleServiceImpl(
            SysRoleRepository roleRepository,
            SysPermissionRepository permissionRepository,
            SysUserRepository userRepository
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
    }

    @Override
    public List<RoleDTO> listRoles() {
        List<SysRole> roles = roleRepository.findAll();
        return roles.stream()
                .map(r -> RoleDTO.from(r, userRepository.countByRoleId(r.getId())))
                .collect(Collectors.toList());
    }

    @Override
    public SysRole getRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "角色不存在"));
    }

    @Override
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

    @Override
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

    @Override
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
