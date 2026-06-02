package com.zjl.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.common.exception.BizException;
import com.zjl.platform.dto.RoleDTO;
import com.zjl.platform.entity.SysPermission;
import com.zjl.platform.entity.SysRole;
import com.zjl.platform.mapper.SysPermissionMapper;
import com.zjl.platform.mapper.SysRoleMapper;
import com.zjl.platform.mapper.SysUserMapper;
import com.zjl.platform.service.RoleService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Transactional
public class RoleServiceImpl implements RoleService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;
    private final SysUserMapper userMapper;

    public RoleServiceImpl(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper,
                           SysUserMapper userMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
        this.userMapper = userMapper;
    }

    @Override
    public List<RoleDTO> listRoles() {
        List<SysRole> roles = roleMapper.selectList(
                new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
        return roles.stream()
                .map(r -> {
                    Set<SysPermission> perms = getRolePermissions(r.getId());
                    long userCount = userMapper.countByRoleId(r.getId());
                    return RoleDTO.from(r, perms, userCount);
                })
                .collect(Collectors.toList());
    }

    @Override
    public SysRole getRole(Long id) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) {
            throw new BizException(40400, "角色不存在");
        }
        return r;
    }

    @Override
    public SysRole createRole(String code, String name, Set<String> permissionCodes) {
        SysRole existing = roleMapper.selectOne(
                new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code));
        if (existing != null) {
            throw new BizException(40000, "角色 code 已存在");
        }
        SysRole r = new SysRole();
        r.setCode(code);
        r.setName(name);
        roleMapper.insert(r);
        if (permissionCodes != null && !permissionCodes.isEmpty()) {
            List<Long> permIds = resolvePermissionIds(permissionCodes);
            roleMapper.insertRolePermissions(r.getId(), permIds);
        }
        return r;
    }

    @Override
    public SysRole updateRole(Long id, String name, Set<String> permissionCodes) {
        SysRole r = roleMapper.selectById(id);
        if (r == null) {
            throw new BizException(40400, "角色不存在");
        }
        if (name != null) r.setName(name);
        roleMapper.updateById(r);
        if (permissionCodes != null) {
            roleMapper.deleteRolePermissions(id);
            List<Long> permIds = resolvePermissionIds(permissionCodes);
            if (!permIds.isEmpty()) {
                roleMapper.insertRolePermissions(id, permIds);
            }
        }
        return roleMapper.selectById(id);
    }

    @Override
    public void deleteRole(Long id) {
        if (roleMapper.selectById(id) == null) {
            throw new BizException(40400, "角色不存在");
        }
        long userCount = userMapper.countByRoleId(id);
        if (userCount > 0) {
            throw new BizException(40000, "角色下存在用户，无法删除");
        }
        roleMapper.deleteById(id);
    }

    private Set<SysPermission> getRolePermissions(Long roleId) {
        List<Long> permIds = roleMapper.selectPermissionIdsByRoleId(roleId);
        if (permIds.isEmpty()) return Set.of();
        return new HashSet<>(permissionMapper.selectBatchIds(permIds));
    }

    private List<Long> resolvePermissionIds(Set<String> permissionCodes) {
        return permissionCodes.stream()
                .map(code -> {
                    SysPermission perm = permissionMapper.selectOne(
                            new LambdaQueryWrapper<SysPermission>().eq(SysPermission::getCode, code));
                    if (perm == null) {
                        throw new BizException(40000, "权限不存在: " + code);
                    }
                    return perm.getId();
                })
                .toList();
    }
}
