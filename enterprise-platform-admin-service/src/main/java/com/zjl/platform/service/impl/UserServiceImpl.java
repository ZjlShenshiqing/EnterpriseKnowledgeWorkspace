package com.zjl.platform.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.common.exception.BizException;
import com.zjl.common.response.PageResult;
import com.zjl.platform.dto.UserInfoDTO;
import com.zjl.platform.entity.SysDept;
import com.zjl.platform.entity.SysRole;
import com.zjl.platform.entity.SysUser;
import com.zjl.platform.mapper.SysDeptMapper;
import com.zjl.platform.mapper.SysRoleMapper;
import com.zjl.platform.mapper.SysUserMapper;
import com.zjl.platform.service.UserService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final SysDeptMapper deptMapper;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(SysUserMapper userMapper, SysRoleMapper roleMapper,
                           SysDeptMapper deptMapper, BCryptPasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.deptMapper = deptMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PageResult<SysUser> listUsers(String keyword, int page, int size) {
        Page<SysUser> p = new Page<>(page, size);
        Page<SysUser> result;
        if (keyword == null || keyword.isBlank()) {
            result = userMapper.selectPage(p,
                    new LambdaQueryWrapper<SysUser>().orderByDesc(SysUser::getId));
        } else {
            result = userMapper.searchUsers(p, "%" + keyword.trim() + "%");
        }
        return PageResult.of(page, size, result.getTotal(), result.getRecords());
    }

    @Override
    public Map<Long, UserInfoDTO> batchGetUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = userMapper.selectBatchIds(userIds);
        Map<Long, String> deptNames = resolveDeptNames(users);
        return users.stream()
                .collect(Collectors.toMap(SysUser::getId,
                        u -> UserInfoDTO.from(u, deptNames.get(u.getDeptId())),
                        (a, b) -> a));
    }

    @Override
    public List<UserInfoDTO> searchUsers(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        String pattern = "%" + keyword.trim() + "%";
        List<SysUser> users = userMapper.findByUsernameLikeOrRealNameLike(pattern,
                Math.min(limit, 100));
        Map<Long, String> deptNames = resolveDeptNames(users);
        return users.stream()
                .map(u -> UserInfoDTO.from(u, deptNames.get(u.getDeptId())))
                .toList();
    }

    @Override
    public UserStats getUserStats() {
        long total = userMapper.countAll();
        long admin = userMapper.countAdmin();
        List<SysUser> all = userMapper.selectList(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getEnabled, true));
        long enabled = all.size();
        long disabled = total - enabled;
        return new UserStats(total, enabled, admin, disabled);
    }

    @Override
    public SysUser getUser(Long id) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(40400, "用户不存在");
        }
        return u;
    }

    @Override
    public SysUser createUser(String username, String password, String realName,
                               Long deptId, Set<String> roleCodes) {
        if (userMapper.selectOne(
                new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username)) != null) {
            throw new BizException(40000, "用户名已存在");
        }
        if (deptId != null && deptMapper.selectById(deptId) == null) {
            throw new BizException(40400, "部门不存在");
        }
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRealName(realName);
        u.setDeptId(deptId);
        u.setEnabled(true);
        userMapper.insert(u);
        if (roleCodes != null && !roleCodes.isEmpty()) {
            List<Long> roleIds = resolveRoleIds(roleCodes);
            if (!roleIds.isEmpty()) {
                userMapper.insertUserRoles(u.getId(), roleIds);
            }
        }
        return userMapper.selectById(u.getId());
    }

    @Override
    public SysUser updateUser(Long id, String realName, Long deptId,
                               Boolean enabled, Set<String> roleCodes) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(40400, "用户不存在");
        }
        if (realName != null) u.setRealName(realName);
        if (deptId != null) {
            if (deptMapper.selectById(deptId) == null) {
                throw new BizException(40400, "部门不存在");
            }
            u.setDeptId(deptId);
        }
        if (enabled != null) u.setEnabled(enabled);
        userMapper.updateById(u);
        if (roleCodes != null) {
            userMapper.deleteUserRoles(id);
            List<Long> roleIds = resolveRoleIds(roleCodes);
            if (!roleIds.isEmpty()) {
                userMapper.insertUserRoles(id, roleIds);
            }
        }
        return userMapper.selectById(id);
    }

    @Override
    public SysUser updateUserRoles(Long id, Set<String> roleCodes) {
        SysUser u = userMapper.selectById(id);
        if (u == null) {
            throw new BizException(40400, "用户不存在");
        }
        userMapper.deleteUserRoles(id);
        List<Long> roleIds = resolveRoleIds(roleCodes);
        if (!roleIds.isEmpty()) {
            userMapper.insertUserRoles(id, roleIds);
        }
        return userMapper.selectById(id);
    }

    @Override
    public void deleteUser(Long id) {
        if (userMapper.selectById(id) == null) {
            throw new BizException(40400, "用户不存在");
        }
        userMapper.deleteById(id);
    }

    private List<Long> resolveRoleIds(Set<String> roleCodes) {
        return roleCodes.stream()
                .map(code -> {
                    SysRole role = roleMapper.selectOne(
                            new LambdaQueryWrapper<SysRole>().eq(SysRole::getCode, code));
                    if (role == null) {
                        throw new BizException(40000, "角色不存在: " + code);
                    }
                    return role.getId();
                })
                .toList();
    }

    private Map<Long, String> resolveDeptNames(List<SysUser> users) {
        Set<Long> deptIds = users.stream()
                .map(SysUser::getDeptId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (deptIds.isEmpty()) return Collections.emptyMap();
        List<SysDept> depts = deptMapper.selectBatchIds(deptIds);
        return depts.stream()
                .collect(Collectors.toMap(SysDept::getId, d -> d.getName() != null ? d.getName() : ""));
    }
}
