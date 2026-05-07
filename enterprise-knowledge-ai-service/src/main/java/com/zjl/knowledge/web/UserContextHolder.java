package com.zjl.knowledge.web;

/**
 * 当前线程用户上下文持有器。
 */
public final class UserContextHolder {

    /**
     * 线程本地变量
     */
    private static final ThreadLocal<UserContext> CTX = new ThreadLocal<>();

    private UserContextHolder() {
    }

    /**
     * 设置上下文。
     *
     * @param ctx 用户上下文
     */
    public static void set(UserContext ctx) {
        CTX.set(ctx);
    }

    /**
     * 获取上下文。
     *
     * @return 用户上下文
     */
    public static UserContext get() {
        return CTX.get();
    }

    /**
     * 清理上下文。
     */
    public static void clear() {
        CTX.remove();
    }
}
