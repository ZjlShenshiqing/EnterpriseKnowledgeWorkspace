package com.zjl.security;

public final class UserContext {
    private static final ThreadLocal<Long> userId = new ThreadLocal<>();
    private static final ThreadLocal<String> username = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> isAdmin = new ThreadLocal<>();

    private UserContext() {}

    public static Long userId() { return userId.get(); }
    public static String username() { return username.get(); }
    public static Boolean isAdmin() { return isAdmin.get(); }

    public static void set(Long uid, String uname, Boolean admin) {
        userId.set(uid); username.set(uname); isAdmin.set(admin);
    }

    public static void clear() { userId.remove(); username.remove(); isAdmin.remove(); }
}
