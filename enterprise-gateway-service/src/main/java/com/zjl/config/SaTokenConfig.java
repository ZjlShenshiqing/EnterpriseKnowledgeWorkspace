package com.zjl.config;

import cn.dev33.satoken.context.SaHolder;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.reactor.filter.SaReactorFilter;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
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
 *
 * 这里主要做三件事：
 * 1. 白名单接口直接放行；
 * 2. 非白名单接口统一校验登录；
 * 3. 按模块校验权限码，admin 角色默认拥有所有权限。
 */
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(AppSecurityProperties.class)
public class SaTokenConfig {

    private static final String ROLE_ADMIN = "admin";
    private static final String METHOD_GET = "GET";
    private static final String METHOD_POST = "POST";

    private static final String WS_PATH_PREFIX = "/ws/";

    private final AppSecurityProperties securityProperties;
    private final ObjectMapper objectMapper;

    /**
     * 注册 Sa-Token 的响应式过滤器
     *
     * 作用：
     * 1. 拦截网关中的所有请求；
     * 2. 放行白名单接口；
     * 3. 对非白名单接口执行登录校验和权限校验；
     * 4. 统一处理鉴权失败后的错误响应。
     */
    @Bean
    public SaReactorFilter saReactorFilter() {

        // 从配置文件中读取白名单路径。
        // securityProperties.getWhitelist().getPaths() 通常是 List<String>，
        // 这里转成 String[]，是因为 addExclude(...) 接收的是数组或可变参数。
        String[] whitelist = securityProperties.getWhitelist()
                .getPaths()
                .toArray(String[]::new);

        // 创建 Sa-Token 在 WebFlux / Gateway 环境下使用的过滤器。
        return new SaReactorFilter()
                // 指定需要拦截的路径 "/**" 表示拦截所有请求
                .addInclude("/**")

                // 指定需要排除的路径，白名单中的路径不会进入登录校验和权限校验逻辑
                .addExclude(whitelist)

                // 设置鉴权逻辑
                // 只有不在白名单中的请求，才会执行这里的方法
                .setAuth(obj -> authenticateAndAuthorize())

                // 设置异常处理逻辑
                .setError(this::renderAuthError);
    }

    /**
     * 登录校验 + 权限校验入口
     *
     * 这个方法只会处理"非白名单"的请求
     *
     * 执行流程：
     * 1. 获取当前请求路径；
     * 2. 如果是 WebSocket 请求，走 WebSocket 专用 token 校验；
     * 3. 普通 HTTP 请求先校验是否登录；
     * 4. 如果当前用户是 admin，直接放行；
     * 5. 普通用户继续按模块校验权限码。
     */
    private void authenticateAndAuthorize() {

        // 获取当前请求路径
        // 后面会根据路径判断：
        // 1. 是否是 WebSocket 请求；
        // 2. 是否命中某个业务模块的权限规则
        String path = SaHolder.getRequest().getRequestPath();

        // WebSocket 请求比较特殊
        //
        // 普通 HTTP 请求一般可以从 Header / Cookie 中拿 token，
        // 但 WebSocket 建连时，前端通常会把 token 放在 query 参数里，
        // 例如：/ws/chat?token=xxxx
        //
        // 所以这里单独处理 WebSocket 登录校验
        if (isWebSocketRequest(path)) {
            checkWebSocketLogin();
            return;
        }

        // 普通 HTTP 请求，先统一校验是否已经登录。
        //
        // 如果未登录，Sa-Token 会抛出 NotLoginException，后面会被 setError(this::renderAuthError) 捕获并转成统一 JSON 响应。
        StpUtil.checkLogin();

        // admin 角色拥有系统全部权限
        //
        // 如果当前用户是 admin，就不再继续检查具体权限码，例如 kb:document:read、collab:task:write 等。
        if (StpUtil.hasRole(ROLE_ADMIN)) {
            return;
        }

        // 普通用户继续按业务模块校验权限。
        //
        // 这些方法内部会根据请求路径和请求方法，
        // 判断用户是否拥有对应的权限码。
        checkKnowledgeBasePermissions();
        checkCollaborationPermissions(path);
        checkWorkbenchPermissions();
        checkSystemPermissions(path);
    }

    /**
     * WebSocket 登录校验
     *
     * WebSocket 建连时，前端通常不能像普通 HTTP 请求一样方便地携带 Header，
     * 所以很多场景会把 token 放在 query 参数中，例如：
     *
     * /ws/chat?token=xxxx
     *
     * 校验逻辑：
     * 1. 优先读取 query 参数中的 token；
     * 2. 如果 token 存在，并且能解析出登录用户，则手动设置当前请求 token；
     * 3. 如果 query token 不存在或无效，则回退到 Sa-Token 默认登录校验逻辑。
     */
    private void checkWebSocketLogin() {

        // 从 WebSocket 请求的 query 参数中获取 token。
        // 例如：/ws/chat?token=xxxx
        String token = SaHolder.getRequest().getParam("token");

        // 如果 query 中携带了 token，并且这个 token 是有效的，
        // 说明当前 WebSocket 请求已经可以识别出登录用户。
        if (token != null && !token.isBlank() && StpUtil.getLoginIdByToken(token) != null) {

            // 手动把 query 中的 token 设置到 Sa-Token 当前上下文中。
            // 这样后续代码里再调用 StpUtil.getLoginId()、StpUtil.checkPermission() 等方法时，Sa-Token 就知道当前请求对应的是哪个用户。
            StpUtil.setTokenValue(token);
            return;
        }

        // 如果 query 参数里没有 token，或者 token 无效，就走 Sa-Token 默认的登录校验逻辑
        // 默认逻辑一般会尝试从 Header、Cookie 等位置读取 token
        // 如果仍然无法识别登录状态，就会抛出 NotLoginException
        StpUtil.checkLogin();
    }

    /**
     * 知识库模块权限校验
     *
     * 这里集中维护 /api/kb/** 相关接口的权限规则
     *
     * 说明：
     * 1. 查询类接口一般使用 read 权限；
     * 2. 新增、修改、删除类接口一般使用 write 权限；
     * 3. 特殊接口单独指定权限码
     */
    private void checkKnowledgeBasePermissions() {

        // 知识库文档接口。
        //
        // GET 请求：需要 kb:document:read 权限
        // 非 GET 请求：需要 kb:document:write 权限
        //
        // 例如：
        // GET    /api/kb/documents/list     -> 读权限
        // POST   /api/kb/documents          -> 写权限
        // PUT    /api/kb/documents/{id}     -> 写权限
        // DELETE /api/kb/documents/{id}     -> 写权限
        matchReadWrite(
                "/api/kb/documents/**",
                "kb:document:read",
                "kb:document:write"
        );

        // 知识库文档统计接口（聚合数据，读权限即可）
        SaRouter.match(
                "/api/kb/document-stats",
                () -> StpUtil.checkPermission("kb:document:read")
        );

        // 知识库库表接口
        //
        // GET 请求：查看知识库，需要 kb:bases:read 权限
        // 非 GET 请求：创建、修改、删除知识库，需要 kb:bases:write 权限
        matchReadWrite(
                "/api/kb/bases/**",
                "kb:bases:read",
                "kb:bases:write"
        );

        // 知识库智能问答接口
        //
        // 该接口不是简单的 read / write 类型，
        // 而是一个独立的"使用 AI 问答能力"的权限。
        SaRouter.match(
                "/api/kb/agent/chat",
                () -> StpUtil.checkPermission("kb:agent:chat")
        );

        // Agent 会话管理（列表、历史、归档）
        //
        // 会话是 AI 问答的附属能力，使用同一权限码。
        SaRouter.match(
                "/api/kb/agent/sessions/**",
                () -> StpUtil.checkPermission("kb:agent:chat")
        );

        // Agent 文件上传（问答上下文附件）
        SaRouter.match(
                "/api/kb/agent/upload",
                () -> StpUtil.checkPermission("kb:agent:chat")
        );

        // 知识库处理流水线接口
        //
        // GET 请求：查看流水线，需要 kb:pipeline:read 权限
        // 非 GET 请求：创建、修改、执行流水线，需要 kb:pipeline:write 权限
        matchReadWrite(
                "/api/kb/pipelines/**",
                "kb:pipeline:read",
                "kb:pipeline:write"
        );

        // 知识库分类接口（含 /api/kb/categories/{id} 的 PUT/DELETE）
        //
        // GET  列表/详情 → kb:document:read
        // POST 创建 / PUT 更新 / DELETE 删除 → kb:document:write
        matchReadWrite(
                "/api/kb/categories/**",
                "kb:document:read",
                "kb:document:write"
        );

        // 知识库管理接口（统计、索引重建等）
        //
        // 这些接口需要 admin 角色，控制器内部也有编程式 admin 校验作为双重保障。
        SaRouter.match(
                "/api/kb/admin/**",
                () -> StpUtil.checkRole(ROLE_ADMIN)
        );

        // AI 问答接口（路由已配置，预保护）
        SaRouter.match(
                "/api/ai-qa/**",
                () -> StpUtil.checkPermission("kb:agent:chat")
        );
    }

    /**
     * 协同办公模块权限
     */
    private void checkCollaborationPermissions(String path) {
        matchReadWrite(
                "/api/meetings/**",
                "collab:meeting:read",
                "collab:meeting:write"
        );

        matchReadWrite(
                "/api/tasks/**",
                "collab:task:read",
                "collab:task:write"
        );

        matchReadWrite(
                "/api/todos/**",
                "collab:todo:read",
                "collab:todo:write"
        );

        matchReadWrite(
                "/api/docs/**",
                "collab:doc:read",
                "collab:doc:write"
        );

        SaRouter.match(
                "/api/chat/**",
                () -> StpUtil.checkPermission("collab:chat:use")
        );

        checkApprovalPermissions(path);
        checkWorkflowPermissions();

        matchReadWrite(
                "/api/announcements/**",
                "collab:announcement:read",
                "collab:announcement:write"
        );

        SaRouter.match(
                "/api/contacts/**",
                () -> StpUtil.checkPermission("collab:contact:read")
        );

        SaRouter.match(
                "/api/notifications/**",
                () -> StpUtil.checkPermission("collab:notification:read")
        );

        SaRouter.match(
                "/api/intents/**",
                () -> StpUtil.checkPermission("collab:intent:read")
        );

        SaRouter.match(
                "/api/keyword-mappings/**",
                () -> StpUtil.checkPermission("collab:intent:read")
        );
    }

    /**
     * 审批申请权限。
     */
    private void checkApprovalPermissions(String path) {
        SaRouter.match("/api/approvals", () -> {
            if (SaHolder.getRequest().isMethod(METHOD_POST)) {
                return;
            }
            StpUtil.checkPermission("approval:read");
        });
        SaRouter.match("/api/approvals/**", () -> {
            if (isLoginOnlyApprovalEndpoint(path)) {
                return;
            }
            StpUtil.checkPermission("approval:read");
        });
    }

    /**
     * 工作流任务和模板权限。
     */
    private void checkWorkflowPermissions() {
        SaRouter.match("/api/workflow/tasks/**", () -> {
            if (isGetRequest()) {
                StpUtil.checkPermission("workflow:task:read");
            } else {
                StpUtil.checkPermission("workflow:task:write");
            }
        });
        SaRouter.match("/api/workflow/templates", () -> StpUtil.checkPermission("workflow:template:read"));
        SaRouter.match("/api/workflow/templates/**", () -> StpUtil.checkPermission("workflow:template:read"));
    }

    /**
     * 工作台模块权限
     */
    private void checkWorkbenchPermissions() {
        SaRouter.match(
                "/api/workbench/**",
                () -> StpUtil.checkPermission("workbench:access")
        );
    }

    /**
     * 系统管理模块权限校验
     *
     * 这里集中处理 /api/system/** 下的接口权限
     *
     * 规则：
     * 1. /api/system/users/batch  只需要登录，不需要 admin；
     * 2. /api/system/users/search 只需要登录，不需要 admin；
     * 3. 其他 /api/system/** 接口都需要 admin 角色。
     */
    private void checkSystemPermissions(String path) {

        // 只要请求路径匹配 /api/system/**，就进入系统管理权限判断
        SaRouter.match("/api/system/**", () -> {

            // 部分系统接口允许普通登录用户访问
            //
            // 注意：
            // 能执行到这里，说明前面已经执行过 StpUtil.checkLogin()，
            // 所以这里直接 return，表示"登录即可访问，不再校验 admin"。
            if (isLoginOnlySystemEndpoint(path)) {
                return;
            }

            // 除了上面的特殊接口，其他所有 /api/system/** 接口
            // 都必须拥有 admin 角色才能访问。
            StpUtil.checkRole(ROLE_ADMIN);
        });
    }

    /**
     * 统一处理"GET 读权限，非 GET 写权限"的接口。
     */
    private void matchReadWrite(String pattern, String readPermission, String writePermission) {
        SaRouter.match(pattern, () -> {
            if (isGetRequest()) {
                StpUtil.checkPermission(readPermission);
            } else {
                StpUtil.checkPermission(writePermission);
            }
        });
    }

    private boolean isGetRequest() {
        return SaHolder.getRequest().isMethod(METHOD_GET);
    }

    private boolean isWebSocketRequest(String path) {
        return path != null && path.startsWith(WS_PATH_PREFIX);
    }

    private boolean isLoginOnlySystemEndpoint(String path) {
        return "/api/system/users/batch".equals(path)
                || "/api/system/users/search".equals(path);
    }

    private boolean isLoginOnlyApprovalEndpoint(String path) {
        return "/api/approvals/my".equals(path)
                || ("/api/approvals".equals(path) && SaHolder.getRequest().isMethod(METHOD_POST));
    }

    /**
     * 统一渲染鉴权失败响应
     */
    private String renderAuthError(Throwable throwable) {
        int code = ErrorCode.UNAUTHORIZED.getCode();
        String message = ErrorCode.UNAUTHORIZED.getMessage();

        if (throwable instanceof NotRoleException || throwable instanceof NotPermissionException) {
            code = ErrorCode.FORBIDDEN.getCode();
            message = ErrorCode.FORBIDDEN.getMessage();
        } else if (!(throwable instanceof NotLoginException)) {
            message = throwable.getMessage() != null
                    ? throwable.getMessage()
                    : message;
        }

        Result<Void> body = Results.failure(code, message, traceId());

        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException ex) {
            return ErrorCode.toFailureJson(code, message, traceId());
        }
    }

    private String traceId() {
        return MDC.get(TraceIdHolder.key());
    }
}
