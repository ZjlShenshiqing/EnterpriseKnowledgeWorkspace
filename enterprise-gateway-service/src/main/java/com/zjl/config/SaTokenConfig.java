package com.zjl.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.web.server.ServerWebExchange;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.common.trace.TraceIdHolder;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sa-Token 网关统一鉴权配置。
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AppSecurityProperties.class)
public class SaTokenConfig {

    private final AppSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    /**
     * 注册 Sa-Token 全局过滤器，在网关层统一校验登录与管理员权限。
     */
    @Bean
    public SaReactorFilter saReactorFilter() {
        String[] whitelist = securityProperties.getWhitelist().getPaths()
                .toArray(new String[0]);
        return new SaReactorFilter()
                .addInclude("/**")
                .addExclude(whitelist)
                .setAuth(obj -> {
                    ServerWebExchange exchange = (ServerWebExchange) obj;
                    String path = exchange.getRequest().getURI().getPath();

                    // WebSocket: verify token from query param
                    if (path.startsWith("/ws/")) {
                        String token = exchange.getRequest().getQueryParams().getFirst("token");
                        if (token == null || token.isBlank() || StpUtil.getLoginIdByToken(token) == null) {
                            StpUtil.checkLogin();
                        }
                        StpUtil.setTokenValue(token);
                        return;
                    }

                    StpUtil.checkLogin();

                    // admin 角色拥有所有权限，跳过权限码检查
                    if (StpUtil.hasRole("admin")) {
                        return;
                    }

                    // ── Knowledge Base ──
                    SaRouter.match("/api/kb/documents/**", () -> {
                        if (SaHolder.getRequest().isMethod("GET")) {
                            StpUtil.checkPermission("kb:document:read");
                        } else {
                            StpUtil.checkPermission("kb:document:write");
                        }
                    });
                    SaRouter.match("/api/kb/bases/**", () -> {
                        if (SaHolder.getRequest().isMethod("GET")) {
                            StpUtil.checkPermission("kb:bases:read");
                        } else {
                            StpUtil.checkPermission("kb:bases:write");
                        }
                    });
                    SaRouter.match("/api/kb/agent/chat", () -> StpUtil.checkPermission("kb:agent:chat"));
                    SaRouter.match("/api/kb/pipelines/**", () -> {
                        if (SaHolder.getRequest().isMethod("GET")) {
                            StpUtil.checkPermission("kb:pipeline:read");
                        } else {
                            StpUtil.checkPermission("kb:pipeline:write");
                        }
                    });
                    SaRouter.match("/api/kb/categories", () -> StpUtil.checkPermission("kb:document:read"));

                    // ── Collaboration ──
                    SaRouter.match("/api/meetings/**", () -> {
                        if (SaHolder.getRequest().isMethod("GET")) {
                            StpUtil.checkPermission("collab:meeting:read");
                        } else {
                            StpUtil.checkPermission("collab:meeting:write");
                        }
                    });
                    SaRouter.match("/api/tasks/**", () -> {
                        if (SaHolder.getRequest().isMethod("GET")) {
                            StpUtil.checkPermission("collab:task:read");
                        } else {
                            StpUtil.checkPermission("collab:task:write");
                        }
                    });
                    SaRouter.match("/api/todos/**", () -> {
                        if (SaHolder.getRequest().isMethod("GET")) {
                            StpUtil.checkPermission("collab:todo:read");
                        } else {
                            StpUtil.checkPermission("collab:todo:write");
                        }
                    });
                    SaRouter.match("/api/docs/**", () -> {
                        if (SaHolder.getRequest().isMethod("GET")) {
                            StpUtil.checkPermission("collab:doc:read");
                        } else {
                            StpUtil.checkPermission("collab:doc:write");
                        }
                    });
                    SaRouter.match("/api/chat/**", () -> StpUtil.checkPermission("collab:chat:use"));
                    SaRouter.match("/api/approvals/**", () -> {
                        if (SaHolder.getRequest().isMethod("GET")) {
                            StpUtil.checkPermission("collab:approval:read");
                        } else {
                            StpUtil.checkPermission("collab:approval:write");
                        }
                    });
                    SaRouter.match("/api/announcements/**", () -> {
                        if (SaHolder.getRequest().isMethod("GET")) {
                            StpUtil.checkPermission("collab:announcement:read");
                        } else {
                            StpUtil.checkPermission("collab:announcement:write");
                        }
                    });
                    SaRouter.match("/api/contacts/**", () -> StpUtil.checkPermission("collab:contact:read"));
                    SaRouter.match("/api/notifications/**", () -> StpUtil.checkPermission("collab:notification:read"));
                    SaRouter.match("/api/intents/**", () -> StpUtil.checkPermission("collab:intent:read"));
                    SaRouter.match("/api/keyword-mappings/**", () -> StpUtil.checkPermission("collab:intent:read"));

                    // ── Workbench ──
                    SaRouter.match("/api/workbench/**", () -> StpUtil.checkPermission("workbench:access"));

                    // ── System admin ──
                    SaRouter.match("/api/system/users/batch", () -> {});
                    SaRouter.match("/api/system/users/search", () -> {});
                    SaRouter.match("/api/system/**", () -> StpUtil.checkRole("admin"));
                })
                .setError(this::renderAuthError);
    }

    private String renderAuthError(Throwable throwable) {
        int code = ErrorCode.UNAUTHORIZED.getCode();
        String message = ErrorCode.UNAUTHORIZED.getMessage();
        if (throwable instanceof NotRoleException || throwable instanceof NotPermissionException) {
            code = ErrorCode.FORBIDDEN.getCode();
            message = ErrorCode.FORBIDDEN.getMessage();
        } else if (!(throwable instanceof NotLoginException)) {
            message = throwable.getMessage() != null ? throwable.getMessage() : message;
        }
        Result<Void> body = Results.failure(code, message, traceId());
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            return "{\"code\":\"40100\",\"message\":\"未登录或登录已过期\",\"data\":null,\"traceId\":null}";
        }
    }

    private String traceId() {
        return MDC.get(TraceIdHolder.key());
    }
}
