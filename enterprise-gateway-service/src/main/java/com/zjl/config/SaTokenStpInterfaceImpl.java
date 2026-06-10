package com.zjl.config;

import cn.dev33.satoken.stp.StpInterface;
import com.zjl.domain.SysRole;
import com.zjl.domain.SysUser;
import com.zjl.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Sa-Token 权限 / 角色数据源实现
 *
 * Sa-Token 在执行以下方法时，会回调当前类：
 * - StpUtil.checkPermission(...)
 * - StpUtil.hasPermission(...)
 * - StpUtil.checkRole(...)
 * - StpUtil.hasRole(...)
 *
 * 这里从网关本地用户库中查询用户的角色和权限，
 * 并返回给 Sa-Token 做 RBAC 鉴权判断。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SaTokenStpInterfaceImpl implements StpInterface {

    private static final List<String> ALL_PERMISSIONS = List.of(
            "kb:document:read", "kb:document:write",
            "kb:bases:read", "kb:bases:write",
            "kb:agent:chat",
            "kb:pipeline:read", "kb:pipeline:write",
            "collab:meeting:read", "collab:meeting:write",
            "collab:task:read", "collab:task:write",
            "collab:todo:read", "collab:todo:write",
            "collab:doc:read", "collab:doc:write",
            "collab:chat:use",
            "collab:announcement:read", "collab:announcement:write",
            "collab:contact:read",
            "collab:notification:read",
            "collab:intent:read",
            "approval:read", "approval:write",
            "workflow:task:read", "workflow:task:write",
            "workflow:template:read",
            "workbench:access"
    );

    private static final Map<String, List<String>> ROLE_PERMISSIONS = Map.of(
            "user", List.of("collab:meeting:read", "collab:task:read", "collab:todo:read",
                    "collab:doc:read", "collab:chat:use", "collab:announcement:read",
                    "collab:contact:read", "collab:notification:read", "workbench:access"),
            "manager", List.of("collab:meeting:read", "collab:meeting:write",
                    "collab:task:read", "collab:task:write",
                    "collab:todo:read", "collab:todo:write",
                    "collab:doc:read", "collab:doc:write",
                    "collab:chat:use",
                    "collab:announcement:read", "collab:announcement:write",
                    "collab:contact:read", "collab:notification:read",
                    "approval:read",
                    "workflow:task:read", "workflow:task:write",
                    "workflow:template:read",
                    "workbench:access")
    );

    private final SysUserRepository userRepository;

    /**
     * 返回当前登录用户拥有的权限码列表
     *
     * 当代码中调用：
     * StpUtil.checkPermission("kb:document:read")
     *
     * Sa-Token 会调用这个方法，获取当前用户的所有权限码，
     * 然后判断其中是否包含 "kb:document:read"。
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        SysUser user = findUser(loginId);

        // 用户不存在或已被禁用，返回空权限列表
        if (user == null || !user.isEnabled()) {
            if (user != null && !user.isEnabled()) {
                log.warn("用户已被禁用: userId={}, username={}", user.getId(), user.getUsername());
            }
            return Collections.emptyList();
        }

        // 从用户角色推导权限码，admin 拥有全部权限
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getCode()));
        if (isAdmin) {
            return ALL_PERMISSIONS;
        }
        // 非管理员根据角色返回对应的读权限
        return user.getRoles().stream()
                .flatMap(r -> permissionsForRole(r.getCode()).stream())
                .distinct()
                .toList();
    }

    /**
     * 返回当前登录用户拥有的角色码列表
     *
     * 当代码中调用：
     * StpUtil.checkRole("admin")
     *
     * Sa-Token 会调用这个方法，获取当前用户的所有角色码，
     * 然后判断其中是否包含 "admin"。
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SysUser user = findUser(loginId);

        // 用户不存在或已被禁用，返回空角色列表，表示没有任何角色
        if (user == null) {
            return Collections.emptyList();
        }
        
        // 用户已被禁用，返回空角色列表，禁止访问
        if (!user.isEnabled()) {
            log.warn("用户已被禁用，拒绝访问: userId={}, username={}", user.getId(), user.getUsername());
            return Collections.emptyList();
        }

        // 提取用户拥有的角色编码，例如：admin、user、manager
        return user.getRoles().stream()
                .map(SysRole::getCode)
                .toList();
    }

    /**
     * 根据 Sa-Token 的登录 ID 查询系统用户
     *
     * loginId 是登录成功时写入 Sa-Token 的用户标识
     * 例如登录时执行：
     * StpUtil.login(userId)
     *
     * 那么这里拿到的 loginId 就是 userId
     */
    private SysUser findUser(Object loginId) {
        // loginId 为空，说明无法识别当前登录用户
        if (loginId == null) {
            return null;
        }

        try {
            // Sa-Token 传入的是 Object 类型，这里统一转换成 Long 类型的用户 ID
            Long userId = Long.parseLong(loginId.toString());

            // 从本地用户表查询用户，不存在则返回 null
            return userRepository.findById(userId).orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<String> permissionsForRole(String roleCode) {
        return ROLE_PERMISSIONS.getOrDefault(roleCode, Collections.emptyList());
    }
}
