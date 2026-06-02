package com.zjl.platform.web;

public record UserContext(Long userId, Long departmentId, Long projectId, boolean isAdmin) {

    private static final ThreadLocal<UserContext> HOLDER = new ThreadLocal<>();

    public static void set(UserContext ctx) {
        HOLDER.set(ctx);
    }

    public static UserContext current() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
