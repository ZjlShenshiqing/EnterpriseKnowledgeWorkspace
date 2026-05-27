package com.zjl.config;

import cn.dev33.satoken.stp.StpInterface;
import com.zjl.domain.SysRole;
import com.zjl.domain.SysUser;
import com.zjl.repository.SysUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Sa-Token 权限/角色数据源：从网关本地用户库加载 RBAC 信息。
 */
@Component
@RequiredArgsConstructor
public class SaTokenStpInterfaceImpl implements StpInterface {

    private final SysUserRepository userRepository;

    /**
     * 返回用户权限码列表。
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        SysUser user = findUser(loginId);
        if (user == null) {
            return Collections.emptyList();
        }
        List<String> permissions = new ArrayList<>();
        for (SysRole role : user.getRoles()) {
            role.getPermissions().forEach(p -> permissions.add(p.getCode()));
        }
        return permissions;
    }

    /**
     * 返回用户角色码列表（如 admin）。
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        SysUser user = findUser(loginId);
        if (user == null) {
            return Collections.emptyList();
        }
        return user.getRoles().stream()
                .map(SysRole::getCode)
                .toList();
    }

    private SysUser findUser(Object loginId) {
        if (loginId == null) {
            return null;
        }
        try {
            Long userId = Long.parseLong(loginId.toString());
            return userRepository.findById(userId).orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
