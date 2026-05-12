package com.zjl.security;

import java.util.List;

public final class UserContext {
    private static final ThreadLocal<Long> userId = new ThreadLocal<>();
    private static final ThreadLocal<String> username = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> isAdmin = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> authorities = new ThreadLocal<>();

    private UserContext() {}

    public static Long userId() { return userId.get(); }
    public static String username() { return username.get(); }
    public static Boolean isAdmin() { return isAdmin.get(); }
    public static List<String> authorities() { return authorities.get(); }

    public static void set(Long uid, String uname, Boolean admin) {
        userId.set(uid); username.set(uname); isAdmin.set(admin);
    }

    public static void set(UserInfo info) {
        userId.set(info.userId); username.set(info.username); authorities.set(info.authorities);
    }

    public static void clear() { userId.remove(); username.remove(); isAdmin.remove(); authorities.remove(); }

    public record UserInfo(Long userId, String username, List<String> authorities) {}
}
