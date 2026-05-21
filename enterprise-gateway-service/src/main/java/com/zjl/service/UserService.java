package com.zjl.service;

import com.zjl.common.exception.BizException;
import com.zjl.common.response.PageResult;
import com.zjl.domain.SysDept;
import com.zjl.domain.SysRole;
import com.zjl.domain.SysUser;
import com.zjl.repository.SysDeptRepository;
import com.zjl.repository.SysRoleRepository;
import com.zjl.repository.SysUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理业务逻辑
 *
 * <p>封装 JPA 阻塞调用，供 Controller 通过 Mono.fromCallable 在弹性线程池中调用。</p>
 */
@Service
@Transactional
public class UserService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysDeptRepository deptRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(
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

    /**
     * 分页用户列表
     *
     * @param keyword 搜索关键词（username 或 realName），null 或空则全量
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 分页结果
     */
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

    /**
     * 按 ID 查询用户
     *
     * @param id 用户 ID
     * @return 用户实体
     * @throws BizException(40400) 用户不存在
     */
    public SysUser getUser(Long id) {
        SysUser u = userRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "用户不存在"));
        if (u.getDept() != null) {
            u.getDept().getName();
        }
        return u;
    }

    /**
     * 用户汇总统计
     *
     * @return UserStats(total, enabled, admin, disabled)
     */
    public UserStats getUserStats() {
        long total = userRepository.count();
        long admin = userRepository.countAdmin();
        long enabled = userRepository.findAll().stream().filter(SysUser::isEnabled).count();
        long disabled = total - enabled;
        return new UserStats(total, enabled, admin, disabled);
    }

    /**
     * 创建用户
     *
     * @param username 用户名
     * @param password 明文密码
     * @param realName 真实姓名（可选）
     * @param deptId 部门 ID（可选）
     * @param roleCodes 角色编码集合（可选）
     * @return 创建后的用户
     * @throws BizException(40000) 用户名已存在
     */
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

    /**
     * 更新用户
     *
     * @param id 用户 ID
     * @param realName 真实姓名（null 则不更新）
     * @param deptId 部门 ID（null 则不更新）
     * @param enabled 是否启用（null 则不更新）
     * @param roleCodes 角色编码集合（null 则不更新）
     * @return 更新后的用户
     */
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

    /**
     * 删除用户
     *
     * @param id 用户 ID
     */
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BizException(40400, "用户不存在");
        }
        userRepository.deleteById(id);
    }

    /**
     * 更新用户角色
     *
     * @param id 用户 ID
     * @param roleCodes 角色编码集合
     * @return 更新后的用户
     */
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

    /**
     * 用户汇总统计数据
     */
    public record UserStats(long total, long enabled, long admin, long disabled) {}
}
