# Auth Unification & Fine-Grained RBAC Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate collaboration service's independent JWT auth, unify all auth through gateway Sa-Token, and add fine-grained RBAC permission checks at the gateway layer.

**Architecture:** All traffic (HTTP + WebSocket) flows through the gateway (:8086). Gateway enforces Sa-Token login + RBAC permission checks, then propagates identity via X-User-Id/X-Department-Id/X-Is-Admin headers to downstream services. Collaboration service drops its own sys_user/sys_dept tables and calls gateway APIs for user lookups.

**Tech Stack:** Sa-Token (Reactor), Spring Cloud Gateway, WebFlux, JPA (gateway), MyBatis-Plus (collaboration), Vue 3

---

### Task 1: Gateway — Add UserInfo DTO for batch query responses

**Files:**
- Create: `enterprise-gateway-service/src/main/java/com/zjl/service/UserInfoDTO.java`

**Purpose:** Define the response shape for batch user queries, consumed by the collaboration service via HTTP.

- [ ] **Step 1: Create UserInfoDTO record**

```java
package com.zjl.service;

import com.zjl.domain.SysUser;

/**
 * 用户简要信息，供下游服务通过 HTTP 批量查询。
 *
 * @param userId   用户 ID
 * @param username 用户名
 * @param realName 姓名
 * @param deptId   部门 ID
 * @param deptName 部门名称
 */
public record UserInfoDTO(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName
) {
    /**
     * 从 SysUser 实体转换。
     */
    public static UserInfoDTO from(SysUser user) {
        return new UserInfoDTO(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                user.getDept() != null ? user.getDept().getId() : null,
                user.getDept() != null ? user.getDept().getName() : null
        );
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-gateway-service/src/main/java/com/zjl/service/UserInfoDTO.java
git commit -m "feat: add UserInfoDTO for batch user query responses"
```

---

### Task 2: Gateway — Add batch user query to UserService

**Files:**
- Modify: `enterprise-gateway-service/src/main/java/com/zjl/service/UserService.java`

**Purpose:** Add batch query and search methods that the SystemAdminController will expose.

- [ ] **Step 1: Read current UserService to find injection point**

```bash
# Already read — file at enterprise-gateway-service/src/main/java/com/zjl/service/UserService.java
```

- [ ] **Step 2: Add batch query and search methods**

Add these methods to `UserService.java` after the existing `getUser` method:

```java
/**
 * 批量查询用户简要信息。
 *
 * @param userIds 用户 ID 列表
 * @return userId → UserInfoDTO
 */
public Map<Long, UserInfoDTO> batchGetUsers(List<Long> userIds) {
    if (userIds == null || userIds.isEmpty()) {
        return Collections.emptyMap();
    }
    List<SysUser> users = userRepository.findAllById(userIds);
    return users.stream()
            .collect(Collectors.toMap(SysUser::getId, UserInfoDTO::from, (a, b) -> a));
}

/**
 * 按关键词搜索用户（用户名或姓名，模糊匹配）。
 *
 * @param keyword 搜索关键词
 * @param limit   最大返回数
 * @return 用户简要信息列表
 */
public List<UserInfoDTO> searchUsers(String keyword, int limit) {
    if (!StringUtils.hasText(keyword)) {
        return Collections.emptyList();
    }
    String pattern = "%" + keyword.trim() + "%";
    Pageable pageable = PageRequest.of(0, Math.min(limit, 100));
    List<SysUser> users = userRepository.findByUsernameLikeOrRealNameLike(pattern, pattern, pageable);
    return users.stream().map(UserInfoDTO::from).toList();
}
```

Add required imports at the top:
```java
import com.zjl.dto.UserInfoDTO;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.util.StringUtils;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
```

Note: If `userRepository` does not have `findByUsernameLikeOrRealNameLike`, add it to `SysUserRepository`:

- [ ] **Step 3: Add search query method to SysUserRepository if needed**

Read the repository, then add to `SysUserRepository.java`:

```java
/**
 * 按用户名或姓名模糊搜索。
 */
@Query("SELECT u FROM SysUser u LEFT JOIN FETCH u.dept WHERE u.enabled = true AND (u.username LIKE ?1 OR u.realName LIKE ?2)")
List<SysUser> findByUsernameLikeOrRealNameLike(String usernamePattern, String realNamePattern, Pageable pageable);
```

- [ ] **Step 4: Commit**

```bash
git add enterprise-gateway-service/src/main/java/com/zjl/service/UserService.java
git commit -m "feat: add batch user query and search to UserService"
```

---

### Task 3: Gateway — Add batch and search endpoints to SystemAdminController

**Files:**
- Modify: `enterprise-gateway-service/src/main/java/com/zjl/web/SystemAdminController.java`

**Purpose:** Expose user lookup endpoints that the collaboration service's GatewayUserClient will call.

- [ ] **Step 1: Add batch query endpoint**

Insert before the "Role endpoints" comment section in `SystemAdminController.java`:

```java
/**
 * 批量查询用户简要信息（供下游服务内部调用）。
 */
@GetMapping("/users/batch")
public Mono<Result<Map<Long, UserInfoDTO>>> batchUsers(
        @RequestParam("ids") List<Long> ids,
        org.springframework.http.server.reactive.ServerHttpRequest request
) {
    log.debug("批量查询用户: ids={}", ids);
    return Mono.fromCallable(() -> Results.success(userService.batchGetUsers(ids)))
            .subscribeOn(Schedulers.boundedElastic());
}

/**
 * 搜索用户（供通讯录等场景）。
 */
@GetMapping("/users/search")
public Mono<Result<List<UserInfoDTO>>> searchUsers(
        @RequestParam(value = "keyword", defaultValue = "") String keyword,
        @RequestParam(value = "limit", defaultValue = "50") int limit,
        org.springframework.http.server.reactive.ServerHttpRequest request
) {
    log.debug("搜索用户: keyword={}", keyword);
    return Mono.fromCallable(() -> Results.success(userService.searchUsers(keyword, limit)))
            .subscribeOn(Schedulers.boundedElastic());
}
```

Add import for `UserInfoDTO` and `Map`:
```java
import com.zjl.dto.UserInfoDTO;
import java.util.Map;
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-gateway-service/src/main/java/com/zjl/web/SystemAdminController.java
git commit -m "feat: add /api/system/users/batch and /search endpoints for downstream user lookups"
```

---

### Task 4: Gateway — Fine-grained RBAC in SaTokenConfig

**Files:**
- Modify: `enterprise-gateway-service/src/main/java/com/zjl/config/SaTokenConfig.java`

**Purpose:** Replace the coarse admin-role-only check with path+method-level permission code checks.

- [ ] **Step 1: Rewrite SaReactorFilter.setAuth()**

Replace the existing `SaReactorFilter` bean method body in `SaTokenConfig.java`. Keep the `whitelist` and `return new SaReactorFilter()` structure, but replace the `setAuth` lambda:

```java
@Bean
public SaReactorFilter saReactorFilter() {
    String[] whitelist = securityProperties.getWhitelist().getPaths()
            .toArray(new String[0]);
    return new SaReactorFilter()
            .addInclude("/**")
            .addExclude(whitelist)
            .setAuth(obj -> {
                StpUtil.checkLogin();

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

                // ── System admin (non-batch endpoints) ──
                SaRouter.match("/api/system/users/batch", () -> {});
                SaRouter.match("/api/system/users/search", () -> {});
                SaRouter.match("/api/system/**", () -> StpUtil.checkRole("admin"));
            })
            .setError(this::renderAuthError);
}
```

Add import at top:
```java
import cn.dev33.satoken.context.SaHolder;
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-gateway-service/src/main/java/com/zjl/config/SaTokenConfig.java
git commit -m "feat: replace coarse admin-role check with fine-grained RBAC permission codes"
```

---

### Task 5: Gateway — WebSocket routing, token resolver, and whitelist

**Files:**
- Modify: `enterprise-gateway-service/src/main/java/com/zjl/config/SaTokenConfig.java`
- Modify: `enterprise-gateway-service/src/main/resources/application.yml`

**Purpose:** Configure gateway to proxy WebSocket connections to the collaboration service and handle WebSocket auth via query param token.

- [ ] **Step 1: Update application.yml — add WebSocket route and whitelist entry**

In `application.yml`, under `spring.cloud.gateway.routes`, add after the existing routes:

```yaml
        - id: collaboration-ws
          uri: lb://enterprise-collaboration-service
          predicates:
            - Path=/ws/**
```

Update the whitelist to include `/ws/**`:

```yaml
app:
  security:
    whitelist:
      paths:
        - /api/system/health
        - /api/auth/login
        - /actuator/health
        - /actuator/info
        - /ws/**
```

- [ ] **Step 2: Add WebSocket token verification to SaReactorFilter**

In `SaTokenConfig.java`, at the start of the `setAuth` lambda (before `StpUtil.checkLogin()`), add WebSocket token handling:

```java
.setAuth(obj -> {
    ServerWebExchange exchange = (ServerWebExchange) obj;
    String path = exchange.getRequest().getURI().getPath();

    // WebSocket: verify token from query param (Sa-Token does not natively read query params)
    if (path.startsWith("/ws/")) {
        String token = exchange.getRequest().getQueryParams().getFirst("token");
        Object loginId = StpUtil.getLoginIdByToken(token);
        if (token == null || token.isBlank() || loginId == null) {
            throw new NotLoginException("WebSocket token 无效或已过期");
        }
        // Set token in context so IdentityPropagationGlobalFilter picks it up
        StpUtil.setTokenValue(token);
        return;
    }

    StpUtil.checkLogin();
    // ... rest of RBAC ...
})
```

This avoids needing a custom token resolver — it directly calls `StpUtil.getLoginIdByToken()` to verify the Sa-Token session exists in Redis. Add import at top:

```java
import cn.dev33.satoken.exception.NotLoginException;
```

- [ ] **Step 3: Commit**

```bash
git add enterprise-gateway-service/src/main/resources/application.yml
git add enterprise-gateway-service/src/main/java/com/zjl/config/SaTokenConfig.java
git commit -m "feat: add WebSocket gateway route and query param token support"
```

---

### Task 6: Collaboration — Delete independent auth files

**Files:**
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/util/JwtUtil.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/util/JwtClaimsSupport.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/JwtAuthFilter.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/MutableRequestWrapper.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/config/FilterConfig.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/AuthController.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/UserLoginService.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/impl/UserLoginServiceImpl.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysUser.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysDept.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/SysUserMapper.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/SysDeptMapper.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/dto/UserLoginReqDTO.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/dto/UserLoginRespDTO.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/dto/UserRegisterReqDTO.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/dto/UserRegisterRespDTO.java`
- Delete: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/dto/UserDeletionReqDTO.java`
- Modify: `enterprise-collaboration-service/src/main/resources/application.yml`

**Purpose:** Remove the entire independent JWT auth system from the collaboration service.

- [ ] **Step 1: Delete all auth-related files**

```bash
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/util/JwtUtil.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/util/JwtClaimsSupport.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/JwtAuthFilter.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/MutableRequestWrapper.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/config/FilterConfig.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/AuthController.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/UserLoginService.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/service/impl/UserLoginServiceImpl.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysUser.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/entity/SysDept.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/SysUserMapper.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/mapper/SysDeptMapper.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/dto/UserLoginReqDTO.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/dto/UserLoginRespDTO.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/dto/UserRegisterReqDTO.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/dto/UserRegisterRespDTO.java
rm enterprise-collaboration-service/src/main/java/com/zjl/collaboration/dto/UserDeletionReqDTO.java
```

- [ ] **Step 2: Remove auth.jwt config from application.yml**

In `application.yml`, delete the `auth:` section:

```yaml
auth:
  jwt:
    expiration: 86400000
```

- [ ] **Step 3: Commit**

```bash
git add -A enterprise-collaboration-service/
git commit -m "feat: remove collaboration service independent JWT auth system"
```

---

### Task 7: Collaboration — Create GatewayUserClient

**Files:**
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/integration/GatewayUserClient.java`
- Create: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/integration/UserInfo.java`

**Purpose:** Replace local DB user lookups with HTTP calls to the gateway's user API.

- [ ] **Step 1: Create UserInfo record**

```java
package com.zjl.collaboration.integration;

/**
 * 用户简要信息，从网关 HTTP 接口获取。
 */
public record UserInfo(
        Long userId,
        String username,
        String realName,
        Long deptId,
        String deptName
) {}
```

- [ ] **Step 2: Create GatewayUserClient**

```java
package com.zjl.collaboration.integration;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HTTP 客户端，调用网关用户接口获取用户信息。
 */
@Slf4j
@Component
public class GatewayUserClient {

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.gateway.url:http://localhost:8086}")
    private String gatewayUrl;

    /**
     * 批量查询用户信息。
     *
     * @param userIds 用户 ID 列表
     * @return userId → UserInfo
     */
    public Map<Long, UserInfo> batchQuery(List<Long> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        String ids = userIds.stream().map(String::valueOf).collect(Collectors.joining(","));
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayUrl + "/api/system/users/batch?ids=" + ids))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
                Object data = body.get("data");
                if (data instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> dataMap = (Map<String, Object>) data;
                    Map<Long, UserInfo> result = new LinkedHashMap<>();
                    dataMap.forEach((k, v) -> {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> userMap = (Map<String, Object>) v;
                        Long userId = toLong(userMap.get("userId"));
                        result.put(userId, new UserInfo(
                                userId,
                                (String) userMap.get("username"),
                                (String) userMap.get("realName"),
                                toLong(userMap.get("deptId")),
                                (String) userMap.get("deptName")
                        ));
                    });
                    return result;
                }
            }
            log.warn("批量查询用户失败: status={}", response.statusCode());
        } catch (Exception e) {
            log.warn("批量查询用户异常: {}", e.getMessage());
        }
        return Collections.emptyMap();
    }

    /**
     * 按用户名查用户信息（ContactController 搜索）。
     */
    public UserInfo getById(Long userId) {
        if (userId == null) return null;
        Map<Long, UserInfo> map = batchQuery(List.of(userId));
        return map.get(userId);
    }

    /**
     * 按关键词搜索用户。
     */
    public List<UserInfo> search(String keyword, int limit) {
        if (keyword == null || keyword.isBlank()) return Collections.emptyList();
        try {
            String encoded = java.net.URLEncoder.encode(keyword.trim(), "UTF-8");
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(gatewayUrl + "/api/system/users/search?keyword=" + encoded + "&limit=" + limit))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> body = objectMapper.readValue(response.body(), new TypeReference<>() {});
                Object data = body.get("data");
                if (data instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> list = (List<Map<String, Object>>) data;
                    return list.stream().map(m -> new UserInfo(
                            toLong(m.get("userId")),
                            (String) m.get("username"),
                            (String) m.get("realName"),
                            toLong(m.get("deptId")),
                            (String) m.get("deptName")
                    )).toList();
                }
            }
        } catch (Exception e) {
            log.warn("搜索用户异常: {}", e.getMessage());
        }
        return Collections.emptyList();
    }

    private static Long toLong(Object v) {
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s) {
            try { return Long.parseLong(s); } catch (NumberFormatException e) {}
        }
        return null;
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/integration/UserInfo.java
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/integration/GatewayUserClient.java
git commit -m "feat: add GatewayUserClient for user lookups via gateway HTTP API"
```

---

### Task 8: Collaboration — Refactor ContactController

**Files:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ContactController.java`

**Purpose:** Replace local SysUserMapper/SysDeptMapper queries with GatewayUserClient calls.

- [ ] **Step 1: Rewrite ContactController**

```java
package com.zjl.collaboration.web;

import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@Slf4j
@RestController
@RequestMapping("/api/contacts")
@RequiredArgsConstructor
public class ContactController {

    private final GatewayUserClient gatewayUserClient;

    @GetMapping("/departments")
    public Result<List<Map<String, Object>>> listDepartments() {
        return Results.success(Collections.emptyList());
    }

    @GetMapping("/users")
    public Result<List<Map<String, Object>>> listUsers(
            @RequestParam(required = false) Long deptId,
            @RequestParam(required = false, defaultValue = "") String keyword,
            @RequestParam(required = false, defaultValue = "200") int limit) {
        List<UserInfo> users;
        if (keyword != null && !keyword.isBlank()) {
            users = gatewayUserClient.search(keyword, limit);
        } else {
            users = gatewayUserClient.search("", limit);
        }
        List<Map<String, Object>> result = users.stream()
                .filter(u -> deptId == null || deptId.equals(u.deptId()))
                .map(u -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", u.userId());
                    m.put("username", u.username());
                    m.put("realName", u.realName());
                    m.put("deptId", u.deptId());
                    return m;
                })
                .toList();
        return Results.success(result);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ContactController.java
git commit -m "feat: refactor ContactController to use GatewayUserClient"
```

---

### Task 9: Collaboration — Refactor WebSocket handlers

**Files:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ChatWebSocketHandler.java`
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocWebSocketHandler.java`

**Purpose:** Remove JWT dependency from WebSocket handlers. Read user identity from X-User-Id header injected by the gateway.

- [ ] **Step 1: Rewrite ChatWebSocketHandler**

```java
package com.zjl.collaboration.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.service.ImMessageConsumer;
import com.zjl.collaboration.service.ImMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final CloseStatus AUTH_FAILED = new CloseStatus(4002, "用户未认证");

    private final ObjectMapper mapper = new ObjectMapper();
    private final ImMessageService imMessageService;
    private final GatewayUserClient gatewayUserClient;

    public ChatWebSocketHandler(ImMessageService imMessageService,
                                 GatewayUserClient gatewayUserClient) {
        this.imMessageService = imMessageService;
        this.gatewayUserClient = gatewayUserClient;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserId(session);
        if (userId == null) {
            session.close(AUTH_FAILED);
            return;
        }
        log.info("Chat WS连接: userId={}", userId);
        ImMessageConsumer.onlineUsers.put(userId, session);
        broadcastStatus(userId, "online");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long senderId = getUserId(session);
        if (senderId == null) return;
        Map<String, Object> msg = mapper.readValue(message.getPayload(), Map.class);
        Long convId = Long.valueOf(msg.get("conversationId").toString());
        String content = msg.get("content") != null ? msg.get("content").toString() : "";
        String clientMsgId = msg.get("clientMsgId") != null ? msg.get("clientMsgId").toString() : "";

        UserInfo sender = gatewayUserClient.getById(senderId);
        String senderName = sender != null ? sender.realName() : null;

        Map<String, Object> ack = imMessageService.send(senderId, senderName, convId, content, clientMsgId);
        String ackJson = mapper.writeValueAsString(ack);
        session.sendMessage(new TextMessage(ackJson));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) {
            log.info("Chat WS断开: userId={}", userId);
            ImMessageConsumer.onlineUsers.remove(userId);
            broadcastStatus(userId, "offline");
        }
    }

    private void broadcastStatus(Long userId, String status) {
        Map<String, Object> out = Map.of("type", "status", "userId", userId, "status", status);
        try {
            String json = mapper.writeValueAsString(out);
            for (var s : ImMessageConsumer.onlineUsers.values()) {
                if (s.isOpen()) s.sendMessage(new TextMessage(json));
            }
        } catch (Exception ignored) {}
    }

    private Long getUserId(WebSocketSession session) {
        String userIdHeader = (String) session.getHandshakeHeaders()
                .getFirst("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            return Long.parseLong(userIdHeader);
        }
        return null;
    }
}
```

- [ ] **Step 2: Rewrite DocWebSocketHandler**

```java
package com.zjl.collaboration.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zjl.collaboration.service.DocOTService;
import com.zjl.collaboration.service.DocPermissionService;
import com.zjl.collaboration.service.DocPermissionService.Permission;
import com.zjl.collaboration.service.DocPresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class DocWebSocketHandler extends TextWebSocketHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocOTService docOTService;
    private final DocPresenceService presenceService;
    private final DocPermissionService permissionService;

    private final Map<String, UserContext> sessionUsers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }
            String userIdHeader = session.getHandshakeHeaders().getFirst("X-User-Id");
            if (userIdHeader == null || userIdHeader.isBlank()) {
                session.close(CloseStatus.POLICY_VIOLATION);
                return;
            }
            Long userId = Long.parseLong(userIdHeader);
            String userName = session.getHandshakeHeaders().getFirst("X-User-Name");
            if (userName == null || userName.isBlank()) {
                userName = String.valueOf(userId);
            }
            sessionUsers.put(session.getId(), new UserContext(userId, userName));
            log.info("WS连接建立: sessionId={}, userId={}", session.getId(), userId);
        } catch (Exception e) {
            log.error("WebSocket 认证失败: session={}", session.getId(), e);
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {}
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode msg = OBJECT_MAPPER.readTree(message.getPayload());
            String action = msg.path("action").asText();
            UserContext user = sessionUsers.get(session.getId());
            if (user == null) {
                sendError(session, "未认证");
                return;
            }

            switch (action) {
                case "sub" -> handleSubscribe(session, msg, user);
                case "op" -> handleOperation(session, msg, user);
                case "cursor" -> handleCursor(session, msg, user);
                case "presence" -> handlePresence(session, msg, user);
                default -> log.warn("未知 action 类型: {}", action);
            }
        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败", e);
        }
    }

    private void handleSubscribe(WebSocketSession session, JsonNode msg, UserContext user) {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) {
            sendError(session, "docId 不能为空");
            return;
        }
        Permission perm = permissionService.checkPermission(docId, user.userId(), null);
        if (perm == null) {
            log.warn("WS订阅拒绝: docId={}, userId={}", docId, user.userId());
            sendError(session, "无权访问此文档");
            return;
        }
        presenceService.join(docId, session.getId(), user.userId(), user.userName(), session);
        presenceService.trackSubscription(session.getId(), docId);

        DocOTService.DocSnapshot snapshot = docOTService.getDocument(docId);
        if (snapshot != null) {
            ObjectNode initMsg = OBJECT_MAPPER.createObjectNode();
            initMsg.put("action", "init");
            initMsg.put("docId", docId);
            initMsg.put("content", snapshot.content());
            initMsg.put("version", snapshot.version());
            initMsg.put("permission", perm.name());
            send(session, initMsg);
        }
        broadcastPresence(docId, user.userId(), user.userName(), true, session.getId());
    }

    private void handleOperation(WebSocketSession session, JsonNode msg, UserContext user) throws Exception {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) {
            sendError(session, "docId 不能为空");
            return;
        }
        int baseVersion = msg.path("version").asInt();
        JsonNode ops = msg.path("ops");
        if (!ops.isArray() || ops.isEmpty()) {
            sendError(session, "ops 不能为空");
            return;
        }
        try {
            JsonNode transformedOps = docOTService.submitOperation(docId, user.userId(), ops, baseVersion);
            ObjectNode ack = OBJECT_MAPPER.createObjectNode();
            ack.put("action", "ack");
            ack.put("docId", docId);
            ack.put("version", baseVersion + 1);
            send(session, ack);

            ObjectNode broadcast = OBJECT_MAPPER.createObjectNode();
            broadcast.put("action", "op");
            broadcast.put("docId", docId);
            broadcast.set("ops", transformedOps);
            broadcast.put("version", baseVersion + 1);
            broadcast.put("userId", user.userId());
            for (var entry : presenceService.getSubscribers(docId).entrySet()) {
                if (!entry.getKey().equals(session.getId())) {
                    send(entry.getValue().session(), broadcast);
                }
            }
        } catch (Exception e) {
            log.error("OT 操作处理失败: docId={}, version={}", docId, baseVersion, e);
            sendError(session, "操作冲突，请刷新页面");
        }
    }

    private void handleCursor(WebSocketSession session, JsonNode msg, UserContext user) {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) return;
        ObjectNode cursorMsg = OBJECT_MAPPER.createObjectNode();
        cursorMsg.put("action", "cursor");
        cursorMsg.put("docId", docId);
        cursorMsg.put("userId", user.userId());
        cursorMsg.put("userName", user.userName());
        cursorMsg.set("range", msg.path("range"));
        for (var entry : presenceService.getSubscribers(docId).entrySet()) {
            if (!entry.getKey().equals(session.getId())) {
                send(entry.getValue().session(), cursorMsg);
            }
        }
    }

    private void handlePresence(WebSocketSession session, JsonNode msg, UserContext user) {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) return;
        broadcastPresence(docId, user.userId(), user.userName(), msg.path("online").asBoolean(true), session.getId());
    }

    private void broadcastPresence(Long docId, Long userId, String userName, boolean online, String excludeSessionId) {
        ObjectNode presenceMsg = OBJECT_MAPPER.createObjectNode();
        presenceMsg.put("action", "presence");
        presenceMsg.put("docId", docId);
        presenceMsg.put("userId", userId);
        presenceMsg.put("userName", userName);
        presenceMsg.put("online", online);
        for (var entry : presenceService.getSubscribers(docId).entrySet()) {
            if (!entry.getKey().equals(excludeSessionId)) {
                send(entry.getValue().session(), presenceMsg);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UserContext user = sessionUsers.remove(session.getId());
        if (user == null) {
            log.info("WS连接断开: sessionId={}", session.getId());
            return;
        }
        log.info("WS连接断开: sessionId={}, userId={}", session.getId(), user.userId());
        Set<Long> docIds = presenceService.removeSession(session.getId());
        for (Long docId : docIds) {
            presenceService.leave(docId, session.getId());
            broadcastPresence(docId, user.userId(), user.userName(), false, session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 传输错误: session={}", session.getId(), exception);
    }

    private void send(WebSocketSession session, JsonNode message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(OBJECT_MAPPER.writeValueAsString(message)));
            }
        } catch (Exception e) {
            log.error("发送消息失败: session={}", session.getId(), e);
        }
    }

    private void sendError(WebSocketSession session, String errMsg) {
        try {
            ObjectNode error = OBJECT_MAPPER.createObjectNode();
            error.put("action", "error");
            error.put("message", errMsg);
            send(session, error);
        } catch (Exception ignored) {}
    }

    private record UserContext(Long userId, String userName) {}
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ChatWebSocketHandler.java
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocWebSocketHandler.java
git commit -m "feat: refactor WebSocket handlers to use gateway identity headers"
```

---

### Task 10: Collaboration — Refactor remaining controllers

**Files:**
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ChatController.java`
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/TaskController.java`
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/AnnouncementController.java`
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/ApprovalController.java`
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocController.java`
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocCommentController.java`
- Modify: `enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/DocShareController.java`

**Purpose:** Replace all SysUserMapper/SysDeptMapper injections with GatewayUserClient, and remove SysUser/SysDept entity references.

- [ ] **Step 1: Refactor ChatController**

Read `ChatController.java`, then:
1. Replace `import com.zjl.collaboration.mapper.SysUserMapper;` with `import com.zjl.collaboration.integration.GatewayUserClient;` and `import com.zjl.collaboration.integration.UserInfo;`
2. Replace `private final SysUserMapper userMapper;` with `private final GatewayUserClient gatewayUserClient;`
3. In the `conversations` method, replace `SysUser u = userMapper.selectById(uid);` patterns with `UserInfo u = gatewayUserClient.getById(uid);` and adjust property accessors (`u.getRealName()` → `u.realName()`)
4. In the method that builds conversation member maps, replace `Map<Long, SysUser> userMap` with `Map<Long, UserInfo> userMap`

- [ ] **Step 2: Refactor TaskController**

Read `TaskController.java`, then:
1. Replace `import com.zjl.collaboration.mapper.SysUserMapper;` with `import com.zjl.collaboration.integration.GatewayUserClient;` and `import com.zjl.collaboration.integration.UserInfo;`
2. Replace `private final SysUserMapper userMapper;` with `private final GatewayUserClient gatewayUserClient;`
3. Replace `SysUser u = userMapper.selectById(...)` with `UserInfo u = gatewayUserClient.getById(...)` and adjust property accessors

- [ ] **Step 3: Refactor AnnouncementController**

Read `AnnouncementController.java`, then:
1. Replace `import com.zjl.collaboration.mapper.SysUserMapper;` and `com.zjl.collaboration.entity.SysUser` with `import com.zjl.collaboration.integration.GatewayUserClient;` and `import com.zjl.collaboration.integration.UserInfo;`
2. Replace `private final SysUserMapper userMapper;` with `private final GatewayUserClient gatewayUserClient;`
3. Replace `SysUser user = userMapper.selectById(userId);` with `UserInfo user = gatewayUserClient.getById(userId);` and adjust property accessors

- [ ] **Step 4: Refactor ApprovalController**

Read `ApprovalController.java`, then:
1. Replace `import com.zjl.collaboration.mapper.SysUserMapper;` with `import com.zjl.collaboration.integration.GatewayUserClient;` and `import com.zjl.collaboration.integration.UserInfo;`
2. Replace `private final SysUserMapper userMapper;` with `private final GatewayUserClient gatewayUserClient;`
3. Replace `SysUser user = userMapper.selectById(userId);` with `UserInfo user = gatewayUserClient.getById(userId);` and adjust property accessors

- [ ] **Step 5: Refactor DocController**

Read `DocController.java`, then:
1. Replace `import com.zjl.collaboration.mapper.SysUserMapper;` with `import com.zjl.collaboration.integration.GatewayUserClient;` and `import com.zjl.collaboration.integration.UserInfo;`
2. Replace `private final SysUserMapper userMapper;` with `private final GatewayUserClient gatewayUserClient;`
3. Replace all `userMapper.selectById(...)` calls with `gatewayUserClient.getById(...)` and adjust property accessors

- [ ] **Step 6: Refactor DocCommentController**

Read `DocCommentController.java`, then:
1. Replace `import com.zjl.collaboration.mapper.SysUserMapper;` with `import com.zjl.collaboration.integration.GatewayUserClient;` and `import com.zjl.collaboration.integration.UserInfo;`
2. Replace `private final SysUserMapper userMapper;` with `private final GatewayUserClient gatewayUserClient;`
3. Replace `userMapper.selectById(...)` with `gatewayUserClient.getById(...)` and adjust property accessors

- [ ] **Step 7: Refactor DocShareController**

Read `DocShareController.java`, then:
1. Replace `import com.zjl.collaboration.mapper.SysUserMapper;` with `import com.zjl.collaboration.integration.GatewayUserClient;` and `import com.zjl.collaboration.integration.UserInfo;`
2. Replace `private final SysUserMapper userMapper;` with `private final GatewayUserClient gatewayUserClient;`
3. Replace `userMapper.selectById(...)` with `gatewayUserClient.getById(...)` and adjust property accessors

- [ ] **Step 8: Commit all controller refactors**

```bash
git add enterprise-collaboration-service/src/main/java/com/zjl/collaboration/web/
git commit -m "feat: refactor all collaboration controllers to use GatewayUserClient"
```

---

### Task 11: Frontend — Update WebSocket URLs + fix token bug

**Files:**
- Modify: `enterprise-web/src/pages/Documents.vue`
- Modify: `enterprise-web/src/pages/Chats.vue`

**Purpose:** Point WebSocket connections to the gateway (:8086) instead of directly to the collaboration service (:8090). Fix Documents.vue's token reading bug.

- [ ] **Step 1: Fix Documents.vue WebSocket URL and token bug**

Read `Documents.vue` around lines 86-90 (getToken function) and 212-216 (WebSocket connect):

Change `getToken()` from reading `user.accessToken` to reading the token from localStorage correctly:

```javascript
function getToken() {
  try {
    const user = JSON.parse(localStorage.getItem('user') || '{}')
    return user.token || localStorage.getItem('token') || ''
  } catch { return '' }
}
```

Change the WebSocket connection line (line 216):

```javascript
// Before:
ws = new WebSocket(`${protocol}//${host}:8090/ws/docs?token=${encodeURIComponent(token)}`)

// After:
ws = new WebSocket(`${protocol}//${host}:8086/ws/docs?token=${encodeURIComponent(token)}`)
```

- [ ] **Step 2: Fix Chats.vue WebSocket URL**

Read `Chats.vue` around line 365:

Change the WebSocket connection:

```javascript
// Before:
ws = new WebSocket(`ws://${host}:8090/ws/chat?token=${encodeURIComponent(token)}`)

// After:
ws = new WebSocket(`ws://${host}:8086/ws/chat?token=${encodeURIComponent(token)}`)
```

- [ ] **Step 3: Commit**

```bash
git add enterprise-web/src/pages/Documents.vue enterprise-web/src/pages/Chats.vue
git commit -m "feat: point WebSocket connections to gateway port 8086, fix Documents token read"
```

---

### Task 12: Build and verify

**Purpose:** Ensure the entire project compiles after all changes.

- [ ] **Step 1: Build gateway service**

```bash
mvn clean compile -pl enterprise-gateway-service -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 2: Build collaboration service**

```bash
mvn clean compile -pl enterprise-collaboration-service -DskipTests
```

Expected: BUILD SUCCESS

- [ ] **Step 3: Full project build**

```bash
mvn clean compile -DskipTests
```

Expected: BUILD SUCCESS
