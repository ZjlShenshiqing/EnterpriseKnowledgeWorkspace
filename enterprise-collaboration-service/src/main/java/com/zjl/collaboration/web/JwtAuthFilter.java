package com.zjl.collaboration.web;

import com.zjl.collaboration.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter implements Filter {

    private final JwtUtil jwtUtil;

    @Override
    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        String path = request.getRequestURI();

        if (path.startsWith("/api/auth/login")) {
            chain.doFilter(req, res);
            return;
        }

        /**
         * 经网关鉴权后，前端会携带 X-User-Id 等身份头；下游不再重复校验网关签发的 JWT。
         */
        String userIdHeader = request.getHeader("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            chain.doFilter(req, res);
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parse(authHeader.substring(7));
                MutableRequestWrapper wrapper = new MutableRequestWrapper(request);
                wrapper.putHeader("X-User-Id", String.valueOf(claims.get("userId", Long.class)));
                wrapper.putHeader("X-Department-Id", String.valueOf(claims.getOrDefault("deptId", "")));
                wrapper.putHeader("X-Is-Admin", String.valueOf(claims.getOrDefault("isAdmin", false)));
                chain.doFilter(wrapper, res);
                return;
            } catch (ExpiredJwtException e) {
                response.setStatus(401);
                response.getWriter().write("{\"code\":\"40100\",\"message\":\"Token已过期\"}");
                return;
            } catch (Exception e) {
                response.setStatus(401);
                response.getWriter().write("{\"code\":\"40100\",\"message\":\"Token无效\"}");
                return;
            }
        }
        chain.doFilter(req, res);
    }
}
