package com.zjl.service.impl;

import com.zjl.common.exception.BizException;
import com.zjl.common.response.PageResult;
import com.zjl.domain.SysDept;
import com.zjl.domain.SysRole;
import com.zjl.domain.SysUser;
import com.zjl.dto.UserInfoDTO;
import com.zjl.repository.SysDeptRepository;
import com.zjl.repository.SysRoleRepository;
import com.zjl.repository.SysUserRepository;
import com.zjl.service.UserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Collections;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Transactional
public class UserServiceImpl implements UserService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysDeptRepository deptRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserServiceImpl(
            SysUserRepository userRepository,
            SysRoleRepository roleRepository,
            SysDeptRepository deptRepository,
            BCryptPasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.deptRepository = deptRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public PageResult<SysUser> listUsers(String keyword, int page, int size) {
        PageRequest pr = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<SysUser> result;
        if (keyword == null || keyword.isBlank()) {
            result = userRepository.findAll(pr);
        } else {
            result = userRepository.searchUsers(keyword, pr);
        }
        result.getContent().forEach(u -> {
            if (u.getDept() != null) {
                u.getDept().getName();
            }
        });
        return PageResult.of(page, size, result.getTotalElements(), result.getContent());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Map<String, Object>> listDirectoryUsers(Long deptId) {
        return userRepository.findDirectoryUsers(deptId).stream().map(u -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", u.getId());
            row.put("username", u.getUsername());
            row.put("realName", u.getRealName());
            row.put("deptId", u.getDept() != null ? u.getDept().getId() : null);
            row.put("isAdmin", u.getRoles().stream()
                    .anyMatch(r -> "admin".equalsIgnoreCase(r.getCode())));
            return row;
        }).toList();
    }

    @Override
    public SysUser getUser(Long id) {
        SysUser u = userRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "用户不存在"));
        if (u.getDept() != null) {
            u.getDept().getName();
        }
        return u;
    }

    @Override
    public Map<Long, UserInfoDTO> batchGetUsers(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<SysUser> users = userRepository.findAllById(userIds);
        return users.stream()
                .collect(Collectors.toMap(SysUser::getId, UserInfoDTO::from, (a, b) -> a));
    }

    @Override
    public List<UserInfoDTO> searchUsers(String keyword, int limit) {
        if (!StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        String pattern = "%" + keyword.trim() + "%";
        org.springframework.data.domain.Pageable pageable = PageRequest.of(0, Math.min(limit, 100));
        List<SysUser> users = userRepository.findByUsernameLikeOrRealNameLike(pattern, pattern, pageable);
        return users.stream().map(UserInfoDTO::from).toList();
    }

    @Override
    public UserStats getUserStats() {
        long total = userRepository.count();
        long admin = userRepository.countAdmin();
        long enabled = userRepository.findAll().stream().filter(SysUser::isEnabled).count();
        long disabled = total - enabled;
        return new UserStats(total, enabled, admin, disabled);
    }

    @Override
    public SysUser createUser(String username, String password, String realName,
                               Long deptId, Set<String> roleCodes) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BizException(40000, "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRealName(realName);
        if (deptId != null) {
            SysDept dept = deptRepository.findById(deptId)
                    .orElseThrow(() -> new BizException(40400, "部门不存在"));
            u.setDept(dept);
        }
        if (roleCodes != null && !roleCodes.isEmpty()) {
            Set<SysRole> roles = resolveRoles(roleCodes);
            u.setRoles(roles);
        }
        return getUser(userRepository.save(u).getId());
    }

    @Override
    public SysUser updateUser(Long id, String realName, Long deptId,
                               Boolean enabled, Set<String> roleCodes) {
        SysUser u = userRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "用户不存在"));
        if (realName != null) u.setRealName(realName);
        if (deptId != null) {
            SysDept dept = deptRepository.findById(deptId)
                    .orElseThrow(() -> new BizException(40400, "部门不存在"));
            u.setDept(dept);
        }
        if (enabled != null) u.setEnabled(enabled);
        if (roleCodes != null) {
            Set<SysRole> roles = resolveRoles(roleCodes);
            u.setRoles(roles);
        }
        return userRepository.save(u);
    }

    @Override
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BizException(40400, "用户不存在");
        }
        userRepository.deleteById(id);
    }

    @Override
    public SysUser updateUserRoles(Long id, Set<String> roleCodes) {
        SysUser u = userRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "用户不存在"));
        Set<SysRole> roles = resolveRoles(roleCodes);
        u.setRoles(roles);
        return userRepository.save(u);
    }

    /**
     * 将角色编码集合转为 SysRole 实体集合
     */
    private Set<SysRole> resolveRoles(Set<String> roleCodes) {
        return roleCodes.stream()
                .map(code -> roleRepository.findByCode(code)
                        .orElseThrow(() -> new BizException(40000, "角色不存在: " + code)))
                .collect(Collectors.toSet());
    }
}
