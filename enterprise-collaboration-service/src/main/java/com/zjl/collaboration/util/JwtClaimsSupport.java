package com.zjl.collaboration.util;

import io.jsonwebtoken.Claims;

/**
 * 从 JWT Claims 解析用户 ID（兼容网关 sub 与历史 userId claim）。
 */
public final class JwtClaimsSupport {

    private JwtClaimsSupport() {
    }

    /**
     * 解析用户 ID：优先 userId claim，其次 subject。
     *
     * @param claims JWT 载荷
     * @return 用户 ID，无法解析时返回 null
     */
    public static Long resolveUserId(Claims claims) {
        if (claims == null) {
            return null;
        }
        Long userId = claims.get("userId", Long.class);
        if (userId != null) {
            return userId;
        }
        String sub = claims.getSubject();
        if (sub == null || sub.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(sub.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
