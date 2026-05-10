package com.zjl.knowledge.web;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.ToString;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 从请求头解析用户上下文
 */
@ToString
@Component
public class UserContextInterceptor implements HandlerInterceptor {

    /**
     * 用户 ID 请求头
     */
    public static final String HEADER_USER_ID = "X-User-Id";

    /**
     * 部门 ID 请求头
     */
    public static final String HEADER_DEPT_ID = "X-Department-Id";

    /**
     * 项目 ID 请求头
     */
    public static final String HEADER_PROJECT_ID = "X-Project-Id";

    /**
     * 是否管理员请求头
     */
    public static final String HEADER_ADMIN = "X-Is-Admin";

    /**
     * 请求进入 Controller 前解析用户。
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (path.startsWith("/actuator")) {
            return true;
        }
        String uid = request.getHeader(HEADER_USER_ID);
        if (!StringUtils.hasText(uid)) {
            throw new BizException(ErrorCode.UNAUTHORIZED);
        }
        try {
            Long userId = Long.parseLong(uid.trim());
            Long dept = parseLongHeader(request, HEADER_DEPT_ID);
            Long project = parseLongHeader(request, HEADER_PROJECT_ID);
            boolean admin = "true".equalsIgnoreCase(request.getHeader(HEADER_ADMIN));
            UserContextHolder.set(UserContext.builder()
                    .userId(userId)
                    .departmentId(dept)
                    .projectId(project)
                    .admin(admin)
                    .build());
            return true;
        } catch (NumberFormatException ex) {
            throw new BizException(ErrorCode.PARAM_INVALID, "用户请求头格式错误");
        }
    }

    /**
     * 请求结束后清理 ThreadLocal。
     */
    @Override
    public void afterCompletion(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler,
            Exception ex
    ) {
        UserContextHolder.clear();
    }

    /**
     * 解析可选 Long 头。
     *
     * @param request 请求
     * @param name 头名称
     * @return Long 或 null
     */
    private Long parseLongHeader(HttpServletRequest request, String name) {
        String v = request.getHeader(name);
        if (!StringUtils.hasText(v)) {
            return null;
        }
        return Long.parseLong(v.trim());
    }
}
