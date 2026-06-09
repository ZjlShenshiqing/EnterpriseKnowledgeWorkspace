package com.zjl.security;

import java.util.List;

/**
 * 请求级别用户上下文
 *
 * <p>基于 ThreadLocal 存储当前请求的用户信息（userId、username、isAdmin、authorities），
 * 在 JWT 认证通过后设置，请求结束后自动清理</p>
 */
public final class UserContext {

    /** 当前请求的用户 ID */
    private static final ThreadLocal<Long> userId = new ThreadLocal<>();
    /** 当前请求的用户名 */
    private static final ThreadLocal<String> username = new ThreadLocal<>();
    /** 当前请求是否为管理员 */
    private static final ThreadLocal<Boolean> isAdmin = new ThreadLocal<>();
    /** 当前请求的权限列表（ROLE_xxx / PERM_xxx） */
    private static final ThreadLocal<List<String>> authorities = new ThreadLocal<>();

    private UserContext() {}

    /** 获取用户 ID */
    public static Long userId() { return userId.get(); }
    /** 获取用户名 */
    public static String username() { return username.get(); }
    /** 获取是否管理员 */
    public static Boolean isAdmin() { return isAdmin.get(); }
    /** 获取权限列表 */
    public static List<String> authorities() { return authorities.get(); }

    /**
     * 设置基础用户信息
     *
     * @param uid   用户 ID
     * @param uname 用户名
     * @param admin 是否管理员
     */
    public static void set(Long uid, String uname, Boolean admin) {
        userId.set(uid);
        username.set(uname);
        isAdmin.set(admin);
    }

    /**
     * 设置完整用户信息（含权限列表和管理员标识）
     *
     * @param uid   用户 ID
     * @param uname 用户名
     * @param admin 是否管理员
     * @param auths 权限列表
     */
    public static void set(Long uid, String uname, Boolean admin, List<String> auths) {
        userId.set(uid);
        username.set(uname);
        isAdmin.set(admin);
        authorities.set(auths);
    }

    /**
     * 设置完整用户信息（含权限列表）
     *
     * @param info 用户信息 record
     */
    public static void set(UserInfo info) {
        userId.set(info.userId);
        username.set(info.username);
        authorities.set(info.authorities);
    }

    /**
     * 清除所有 ThreadLocal，防止内存泄漏和上下文串用
     */
    public static void clear() {
        userId.remove();
        username.remove();
        isAdmin.remove();
        authorities.remove();
    }

    /**
     * 用户信息 record，用于一次携带 userId + username + authorities
     */
    public record UserInfo(Long userId, String username, List<String> authorities) {}
}
