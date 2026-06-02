package com.zjl.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

public class UserContextInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String userId = request.getHeader("X-User-Id");
        String deptId = request.getHeader("X-Department-Id");
        String projectId = request.getHeader("X-Project-Id");
        String isAdmin = request.getHeader("X-Is-Admin");

        UserContext ctx = new UserContext(
                userId != null ? Long.parseLong(userId) : null,
                deptId != null ? Long.parseLong(deptId) : null,
                projectId != null ? Long.parseLong(projectId) : null,
                "true".equalsIgnoreCase(isAdmin)
        );
        UserContext.set(ctx);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
