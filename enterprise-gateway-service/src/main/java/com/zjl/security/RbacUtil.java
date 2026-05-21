package com.zjl.security;

import io.jsonwebtoken.Claims;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * RBAC 解析服务：将 JWT 中的权限信息映射为 Spring Security Authorities
 */
@Service
public class RbacUtil {

    /**
     * 将 JWT Claims 转换为 Spring Security Authentication
     *
     * <p>约定：</p>
     * <ul>
     *   <li>sub: userId</li>
     *   <li>username: 用户名</li>
     *   <li>authorities: 字符串数组（ROLE_xxx / PERM_xxx）</li>
     * </ul>
     */
    public Mono<AbstractAuthenticationToken> toAuthentication(Claims claims) {
        // 从 JWT 取 userId（sub 字段）
        String userId = claims.getSubject();
        // 从 JWT 取用户名
        String username = Objects.toString(claims.get("username"), "");

        // 从 JWT 取权限列表，映射为 GrantedAuthority 集合
        Collection<SimpleGrantedAuthority> authorities = new ArrayList<>();
        Object raw = claims.get("authorities");
        if (raw instanceof List<?> list) {
            for (Object a : list) {
                if (a != null) {
                    authorities.add(new SimpleGrantedAuthority(String.valueOf(a)));
                }
            }
        }

        // 构建 Spring Security 认证令牌（3参构造已自动标记为已认证）
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userId, username, authorities);
        // 附加完整 claims 信息，供后续过滤器使用
        auth.setDetails(claims);
        // 以 Mono 形式返回，适配 WebFlux 响应式链路
        return Mono.just(auth);
    }
}

