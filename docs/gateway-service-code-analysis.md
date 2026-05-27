# enterprise-gateway-service 服务分析

> **文档版本**：v3.0 · **更新日期**：2026-05-25  
> 基于当前 `enterprise-gateway-service` 源码整理，覆盖 JWT 认证、RBAC、路由转发、限流、SystemAdmin 全量 API、WebFlux 编程约束与种子数据。

本文重点解释网关**真实请求顺序**（Security 与 GlobalFilter 的先后）、身份如何透传到下游、RBAC 数据模型，以及生产部署时的常见坑。

**文档结构**

| 章节 | 内容 |
|------|------|
| §1–§4 | 定位、目录树、启动配置、Controller 分组 |
| §5 | **请求链路**（架构图 + 分流 + 正确 Filter 顺序） |
| §6–§8 | Filter 逐类、SecurityConfig、JWT/黑名单 |
| §9–§10 | 登录退出、身份透传与下游契约 |
| §11–§12 | SystemAdmin 全量 API、Service/Repository |
| §13–§14 | 路由表、数据库与种子数据 |
| §15–§16 | 响应/错误码、WebFlux 编程规范 |
| §17 | 阅读路线与问题反查 |
| §18 | 完整代码地图（逐文件） |
| §19–§21 | 附录：REST 速查、调用链、Gotchas |

---

## 1. 服务定位

`enterprise-gateway-service` 是平台 **生产环境主 HTTP 入口**（`:8086`），承担 6 条能力线：

1. **统一路由**：Spring Cloud Gateway + Nacos `lb://` 转发至 knowledge / collaboration / workbench
2. **JWT 认证**：登录签发 HS256 token、请求验签、退出 SHA-256 黑名单
3. **RBAC 管理**：用户/角色/权限/部门 CRUD + `sys_op_log` 操作审计
4. **访问控制**：IP 黑白名单、内存固定窗口限流（默认 120 req/60s/IP）
5. **身份透传**：已认证转发请求注入 `X-User-Id` / `X-Is-Admin`，**移除 Authorization**
6. **全链路追踪**：`X-Trace-Id` 生成/透传 + MDC `%X{traceId}` 日志

| 项 | 值 |
|---|---|
| 端口 | **8086** |
| 数据库 | MySQL `enterprise_gateway` |
| ORM | JPA/Hibernate（`ddl-auto: none`） |
| 运行时 | **WebFlux + Spring Cloud Gateway**（非 Servlet） |
| 服务发现 | Nacos `:8848` → `lb://enterprise-*-service` |
| Java | 17 |
| 启动类 | `GatewaySpringbootStarter`（**无 `@EnableScheduling`**） |

**Maven 坐标**：`com.zjl:enterprise-gateway-service:1.0-SNAPSHOT`  
**父 POM**：`EnterpriseKnowledgeWorkspace`（Spring Boot **3.4.4**，Spring Cloud **2024.0.1**，Nacos **2025.0.0.0**）

### 1.1 平台架构位置

```text
┌─────────────┐     Bearer JWT      ┌──────────────────────────┐
│  enterprise │ ──────────────────► │ enterprise-gateway-service│
│  -web :5173 │                     │         :8086             │
└─────────────┘                     └───────────┬──────────────┘
                                                │
           ┌────────────────────────────────────┼────────────────────────┐
           │ 本地 Controller                    │ lb:// 转发              │
           ▼                                    ▼                        ▼
    /api/auth/*                          knowledge-ai :8083    collaboration :8090
    /api/system/*                        workbench :8084
    /api/contacts/*
```

> Gateway 库 `sys_user` 与 Collaboration 库 `sys_user` **完全独立**。前端登录 Gateway 拿到的 token 仅用于经 `:8086` 的请求；WebSocket（`:8090`）或协同自有 `/api/auth/login` 是另一套认证。

### 1.2 与其他服务文档的交叉引用

| 下游 | 文档 | 端口 |
|------|------|:----:|
| knowledge-ai | `knowledge-service-code-analysis.md` | 8083 |
| collaboration | `collaboration-service-code-analysis.md` | 8090 |
| workbench | `workbench-service-code-analysis.md` | 8084 |

---

## 2. 代码结构树

```text
enterprise-gateway-service/src/main/java/com/zjl/
├── GatewaySpringbootStarter.java
├── config/
│   ├── AppSecurityProperties.java      # app.security.jwt / whitelist
│   └── AppGatewayProperties.java       # app.gateway.ip / rateLimit
├── filter/
│   ├── GatewayTraceIdFilter.java       # WebFilter
│   ├── IpAccessGlobalFilter.java       # GlobalFilter Order=-100
│   ├── SimpleRateLimitGlobalFilter.java# GlobalFilter Order=-90
│   ├── JwtAuthenticationWebFilter.java # Security AUTHENTICATION 位
│   └── IdentityPropagationGlobalFilter.java # GlobalFilter Order=200
├── security/
│   ├── SecurityConfig.java
│   ├── JwtUtil.java
│   ├── RbacUtil.java
│   ├── TokenBlacklistService.java
│   ├── UserContext.java
│   └── PasswordConfig.java
├── web/
│   ├── AuthController.java
│   ├── SystemAdminController.java
│   ├── ContactDirectoryController.java
│   └── GatewayExceptionHandler.java
├── service/
│   ├── UserService.java
│   ├── RoleService.java
│   ├── RoleDTO.java
│   └── OpLogService.java
├── domain/          # SysUser, SysDept, SysRole, SysPermission, SysOpLog, TokenBlacklistEntry
├── repository/      # 6 个 JpaRepository
└── gateway/response/
    └── ApiResponseWriter.java
```

共 **35** 个 Java 源文件。

---

## 3. 启动与配置

### 3.1 启动入口

**文件**：`GatewaySpringbootStarter.java`

```java
@SpringBootApplication
public class GatewaySpringbootStarter { ... }
```

当前**未声明**：

- `@EnableScheduling` — 导致 `TokenBlacklistService.cleanup()` 可能永不执行
- `@EnableConfigurationProperties` — 由 `SecurityConfig` 上的 `@EnableConfigurationProperties(AppSecurityProperties.class)` 启用安全配置；`AppGatewayProperties` 由 Filter 上的 `@EnableConfigurationProperties` 启用

### 3.2 配置加载

```yaml
spring.config.import:
  - optional:classpath:application-secrets.yml
  - optional:nacos:enterprise-gateway-service.yaml
  - optional:nacos:common-config.yaml
```

| 来源 | 典型内容 |
|------|----------|
| `application.yml` | 端口、路由、JWT dev secret、限流默认值 |
| `application-secrets.yml` | `spring.datasource.password`（不入 Git） |
| Nacos | 数据源 URL、生产 JWT secret、IP 名单 |

### 3.3 完整配置项表

| 配置项 | 默认值（application.yml） | 说明 |
|--------|---------------------------|------|
| `server.port` | 8086 | HTTP |
| `spring.application.name` | `enterprise-gateway-service` | Nacos 注册名 |
| `spring.cloud.nacos.discovery.server-addr` | localhost:8848 | 服务发现 |
| `spring.jpa.hibernate.ddl-auto` | none | 不自动 DDL |
| `spring.jpa.open-in-view` | false | 关闭 OSIV |
| `spring.cloud.gateway.default-filters` | PreserveHostHeader | 转发保留 Host |
| `app.security.jwt.secret` | dev 32+ 字符 | HS256 密钥 |
| `app.security.jwt.ttl-seconds` | **86400** | 24h（`AppSecurityProperties` 类默认 7200，被 yml 覆盖） |
| `app.security.whitelist.paths` | login、actuator 等 | Ant 风格路径 |
| `app.gateway.rateLimit.enabled` | true | |
| `app.gateway.rateLimit.requests` | **120** | 窗口内最大请求（类默认 60） |
| `app.gateway.rateLimit.windowSeconds` | 60 | |
| `app.gateway.ip.blacklist` | [] | 精确 IP 匹配 |
| `app.gateway.ip.whitelist` | [] | 非空则仅允许列表内 IP |

### 3.4 鉴权白名单

| 路径 | 说明 |
|------|------|
| `/api/auth/login` | 唯一业务免登入口 |
| `/actuator/health` | 健康检查 |
| `/actuator/info` | 应用信息 |
| `/api/system/health` | yml 配置了但 **无 Controller 实现** |

### 3.5 Maven 依赖摘要

| 依赖 | 用途 |
|------|------|
| `spring-cloud-starter-gateway` | 网关核心 |
| `spring-boot-starter-webflux` | 响应式 Web |
| `spring-boot-starter-security` | 响应式 Security |
| `spring-boot-starter-data-jpa` | RBAC 持久化 |
| `mysql-connector-j` | MySQL |
| `jjwt-*` 0.12.6 | JWT |
| `frameworks-common-spring-boot-starter` | Result、BizException、ErrorCode、TraceIdHolder |
| `spring-cloud-starter-alibaba-nacos-discovery/config` | Nacos |
| `spring-cloud-starter-loadbalancer` | `lb://` 解析 |
| `spring-boot-starter-actuator` | 健康/路由端点 |
| `transmittable-thread-local` | 依赖引入（UserContext 当前用标准 ThreadLocal） |

### 3.6 Actuator

```yaml
management.endpoints.web.exposure.include: health,info,gateway
management.endpoint.health.show-details: always
```

| 端点 | 白名单 | 说明 |
|------|:------:|------|
| `/actuator/health` | ✅ | 免 JWT |
| `/actuator/info` | ✅ | 免 JWT |
| `/actuator/gateway/routes` | ❌ | 需 JWT，可查看运行时路由 |

---

## 4. Controller 分组

### 4.1 本地处理（不匹配 Gateway Route，由 WebFlux Dispatcher 处理）

| Controller | 前缀 | 鉴权 | 返回类型 |
|-----------|------|------|----------|
| `AuthController` | `/api/auth` | login 白名单；logout 需 JWT | `Mono<Result<T>>` |
| `SystemAdminController` | `/api/system` | 类级 `@PreAuthorize("hasRole('ADMIN')")` | `Mono<Result<T>>` |
| `ContactDirectoryController` | `/api/contacts` | 任意已登录用户 | `Mono<Result<T>>` |

### 4.2 转发下游

凡匹配 `application.yml` 中 `spring.cloud.gateway.routes` 的 Path  predicate 的请求，经 GlobalFilter 链后 `NettyRoutingFilter` 转发至 Nacos 实例。

### 4.3 响应格式

Controller 与 Filter 均输出 `Result<T>`：

```json
{
  "code": "200",
  "message": "success",
  "data": { },
  "traceId": "uuid-or-upstream-id"
}
```

| 来源 | 失败码示例 |
|------|-----------|
| Security 401 | `40100` UNAUTHORIZED |
| Security 403 / IP 403 | `40300` FORBIDDEN |
| 限流 429 | `42900` 自定义 message |
| Controller BizException | 业务码如 `40000`、`40400` |
| 参数校验 | `40000` PARAM_INVALID |

Filter 层用 `ApiResponseWriter`；Controller 层用 `GatewayExceptionHandler` 捕获 `BizException`（HTTP 200 + code 非 200）。

---

## 5. 请求链路（重要：顺序与分流）

> **v2 文档误区修正**：IP 限流 **不在** JWT 之前。实际顺序是 **先 Spring Security 验 JWT，再跑 Gateway GlobalFilter（IP/限流/身份透传/路由）**。

### 5.1 真实 Filter 执行顺序

```text
【WebFilter 链 — 进入 Netty 后最早】
  ① GatewayTraceIdFilter          生成/透传 X-Trace-Id，写入 MDC
  ② Spring Security WebFilterChain
       └─ JwtAuthenticationWebFilter  提取 Bearer → 黑名单 → 验签 → UserContext
       └─ UserContext.clear()         请求结束清理（addFilterAfter AUTHENTICATION）

【Gateway GlobalFilter 链 — Security 通过后】
  ③ IpAccessGlobalFilter           Order = -100
  ④ SimpleRateLimitGlobalFilter    Order = -90
  ⑤ … 框架内置 Filter（RoutePredicate、LoadBalancer 等，Order 数千~万级）…
  ⑥ IdentityPropagationGlobalFilter Order = 200（在 RouteToRequestUrl 10000 之前）
  ⑦ RouteToRequestUrlFilter        Order = 10000
  ⑧ NettyRoutingFilter             Order = MAX（转发下游或本地响应）
```

**依据**：

- `IpAccessGlobalFilter.getOrder()` → `-100`
- `IdentityPropagationGlobalFilter.getOrder()` → `200`，注释写明须大于 `SecurityWebFiltersOrder.AUTHENTICATION (100)`
- Spring Cloud Gateway 内置 `RouteToRequestUrlFilter` Order = `10000`

因此：**未带 JWT 的请求会在步骤 ② 被 401 拦截，根本到不了 IP/限流 Filter**。只有白名单路径（如 login）能在无 JWT 情况下进入 ③④。

### 5.2 请求分流总览

```mermaid
flowchart TD
    A[HTTP 请求进入 :8086] --> B[GatewayTraceIdFilter]
    B --> C[Spring Security 链]
    C --> D{路径在白名单?}
    D -->|是| E[permitAll 无 Authentication]
    D -->|否| F{Bearer JWT 有效?}
    F -->|否| Z401[401 JSON ApiResponseWriter]
    F -->|是| G[Authentication 写入 SecurityContext 与 UserContext]
    E --> H[Gateway GlobalFilter 链]
    G --> H
    H --> I[IpAccessGlobalFilter]
    I --> J{IP 允许?}
    J -->|否| Z403[403 JSON]
    J -->|是| K[SimpleRateLimitGlobalFilter]
    K --> L{未超限?}
    L -->|否| Z429[429 JSON]
    L -->|是| M[IdentityPropagationGlobalFilter]
    M --> N{存在 Authentication?}
    N -->|是| O[注入 X-User-Id X-Is-Admin 移除 Authorization]
    N -->|否| P[不修改请求头]
    O --> Q{匹配 Gateway Route?}
    P --> Q
    Q -->|是| R[NettyRoutingFilter 转发下游]
    Q -->|否| S[本地 WebFlux Controller]
    R --> T[响应]
    S --> T
```

### 5.3 两条典型路径对比

| 步骤 | `POST /api/auth/login` | `GET /api/kb/documents` + Bearer |
|------|------------------------|----------------------------------|
| TraceId | ✅ | ✅ |
| Security | 白名单 permitAll | JWT 验签 + UserContext |
| IP/限流 | ✅ 仍执行 | ✅ |
| IdentityPropagation | 无 Authentication，不注入 | 注入 X-User-Id，删 Authorization |
| 路由 | 无 Route → 本地 AuthController | knowledge-ai Route → :8083 |

### 5.4 登录流程

```mermaid
flowchart LR
    subgraph Client
        A[POST /api/auth/login]
    end
    subgraph Gateway
        B[AuthController.login]
        C[SysUserRepository.findByUsername]
        D[BCrypt.matches]
        E[authoritiesOf 角色+权限]
        F[JwtUtil.issueToken]
    end
    A --> B --> C --> D --> E --> F
    F --> G[LoginResponse token userId deptId isAdmin]
```

### 5.5 JWT 校验流程（非白名单请求）

```mermaid
flowchart TD
    A[Authorization Bearer token] --> B{compact JWS 两个点?}
    B -->|否| X[401]
    B -->|是| C[TokenBlacklistService.isBlacklisted]
    C -->|是| X
    C -->|否| D[JwtUtil.parse boundedElastic]
    D -->|过期/签错| X
    D -->|成功| E[RbacUtil.toAuthentication]
    E --> F[JwtAuthenticationWebFilter 成功回调]
    F --> G[exchange AUTHENTICATION 属性]
    F --> H[UserContext.set UserInfo]
    F --> I[继续 Security 链]
```

---

## 6. Filter 逐类说明

### 6.1 GatewayTraceIdFilter

| 项 | 值 |
|----|-----|
| 类型 | `WebFilter`（非 GlobalFilter） |
| 常量 | `TRACE_ID_HEADER = "X-Trace-Id"` |
| 逻辑 | 有头则复用，无则 UUID；写 MDC + 响应头；`doFinally` 清理 MDC |

### 6.2 IpAccessGlobalFilter

| 项 | 值 |
|----|-----|
| Order | **-100** |
| 白名单非空 | IP 不在列表 → 403「IP 未在白名单内」 |
| 黑名单 | IP 在列表 → 403「IP 已被禁止访问」 |
| IP 来源 | `getRemoteAddress()` **仅直连 IP** |

### 6.3 SimpleRateLimitGlobalFilter

| 项 | 值 |
|----|-----|
| Order | **-90** |
| 算法 | 固定窗口：`windowStart = nowSec - nowSec % windowSeconds` |
| 计数 | `ConcurrentHashMap<String, WindowCounter>` key=IP |
| 拒绝 | `count > limit` → HTTP 429，body code=`42900` |
| 关闭 | `rateLimit.enabled=false` 直接放行 |

**窗口示例**（windowSeconds=60）：

```text
时间 10:00:00 ~ 10:00:59 → 同一 windowStart
第 121 次请求 → 429
10:01:00 → 新窗口，计数重置为 1
```

### 6.4 JwtAuthenticationWebFilter

| 项 | 值 |
|----|-----|
| 基类 | `AuthenticationWebFilter` |
| AuthenticationManager | noop — converter 产出已认证 token |
| 成功 | `AUTHENTICATION` 属性 + `UserContext.set(UserInfo)` + log |
| 失败 | `ApiResponseWriter` 401 |

**UserContext 注意**：成功时调用 `UserContext.set(new UserInfo(userId, username, authorities))`，**未设置** `UserContext.isAdmin` ThreadLocal。`SystemAdminController` 日志只用 `UserContext.userId()`。管理员判定依赖 `@PreAuthorize` 读 SecurityContext 的 authorities，不依赖 `UserContext.isAdmin()`。

### 6.5 IdentityPropagationGlobalFilter

| 项 | 值 |
|----|-----|
| Order | **200** |
| 触发 | `resolveAuthentication` 非空 |
| 注入 | `X-User-Id`, `X-Is-Admin` |
| 透传 | 客户端已带 `X-Department-Id` 则保留 |
| 删除 | `Authorization` 头 |
| 未认证 | `switchIfEmpty(chain.filter)` 原样放行 |

**对本地 Controller 的影响**：已认证请求访问 `/api/system/*` 时本 Filter 仍会执行并 mutate 请求对象，但因不走 Netty 转发，mutate 的头**不影响**本地 Handler（仅影响下游转发）。本地 Controller 从 `UserContext` / SecurityContext 取身份，不读这些 Header。

### 6.6 SecurityConfig 装配清单

| 配置 | 值 | 原因 |
|------|-----|------|
| csrf | disable | 无 Cookie Session |
| securityContextRepository | NoOp | 无状态 JWT |
| httpBasic / formLogin | disable | 纯 Bearer |
| authorizeExchange | whitelist + anyExchange.authenticated | |
| exceptionHandling | 401/403 → JSON | 不重定向登录页 |
| addFilterAt | JwtAuthenticationWebFilter @ AUTHENTICATION | 替换默认认证 |
| addFilterAfter | UserContext.clear @ AUTHENTICATION | 防 ThreadLocal 泄漏 |
| @EnableReactiveMethodSecurity | 开 | `@PreAuthorize` 生效 |

---

## 7. JWT 与黑名单

### 7.1 签发（JwtUtil.issueToken）

```java
Jwts.builder()
    .subject(String.valueOf(userId))
    .claim("username", username)
    .claims(extraClaims)           // authorities: List<String>
    .issuedAt(now).expiration(now + ttl)
    .signWith(secretKey, HS256)
```

### 7.2 JWT Payload 示例（解码后）

```json
{
  "sub": "1",
  "username": "admin",
  "authorities": [
    "ROLE_ADMIN",
    "system:user:read",
    "system:user:write",
    "system:role:read",
    "kb:document:read"
  ],
  "iat": 1716633600,
  "exp": 1716720000
}
```

### 7.3 authorities 与 @PreAuthorize

| JWT authorities 条目 | Spring 用途 |
|---------------------|-------------|
| `ROLE_ADMIN` | `hasRole('ADMIN')` — SystemAdmin 全套 |
| `ROLE_MANAGER` | `hasRole('MANAGER')`（若代码中使用） |
| `system:user:write` | 细粒度 permission（当前 Controller 未用 `@PreAuthorize` 到 permission 级） |

登录时 `admin` 角色 → `ROLE_ADMIN`；`isAdmin` 字段另由 `roles.code equalsIgnoreCase admin` 计算，**仅供前端展示**，与 `@PreAuthorize` 独立。

### 7.4 黑名单

| 步骤 | 实现 |
|------|------|
| 存储 | `SHA-256(token)` hex 64 字符 → `sys_token_blacklist.token_hash` |
| 过期 | 取 JWT `exp` 写入 `expires_at` |
| 校验 | 每次 converter `findByTokenHash` |
| 清理 | `@Scheduled(fixedDelay=60000)` delete expired — **需 @EnableScheduling** |

---

## 8. 登录与退出

### 8.1 登录 API

**`POST /api/auth/login`**（白名单）

请求：

```json
{ "username": "admin", "password": "123456" }
```

种子数据中 admin 密码 hash 对应常见测试密码（见 §14.3）。

响应 `LoginResponse`：

| 字段 | 来源 |
|------|------|
| token | JWT |
| userId | `user.id` |
| username | |
| realName | |
| deptId | `user.dept.id` 可 null |
| isAdmin | roles 含 code=admin |

失败：统一 `40100`「用户名或密码错误」（用户不存在、禁用、密码错均相同文案）。

### 8.2 退出 API

**`POST /api/auth/logout`**（需 JWT，**不在**白名单）

- 有 token → blacklist → success
- 无 token → success（幂等）

---

## 9. 身份透传与下游契约

### 9.1 下游如何读身份

| 服务 | 机制 |
|------|------|
| knowledge-ai | `UserContextInterceptor` → `UserContextHolder` |
| workbench | `@RequestHeader X-User-Id` |
| collaboration | `JwtAuthFilter`：有 `X-User-Id` 则跳过协同 JWT |

### 9.2 Header 契约

| Header | Gateway 行为 | 下游 |
|--------|-------------|------|
| `X-User-Id` | 从 JWT sub 注入 | 必填 |
| `X-Is-Admin` | 从 ROLE_ADMIN 注入 true/false | 权限放宽 |
| `X-Department-Id` | **仅透传**客户端已传值 | Knowledge DEPARTMENT 权限 |
| `X-Project-Id` | 未实现 | 文档提及，代码无 |
| `Authorization` | **转发前删除** | 下游不应依赖 Gateway JWT |

### 9.3 前端开发代理（vite.config.js）

```javascript
proxy: {
  '/api/workbench': 'http://localhost:8084',
  '/api/kb':        'http://localhost:8083',  // 含 agent、mcp 前缀外的 kb
  '/api/auth':      'http://localhost:8086',
  '/api':           'http://localhost:8086',  // 兜底 → Gateway
  '/mcp':           'http://localhost:8083',
}
```

**注意**：Vite **未代理 WebSocket**，IM/文档协同 WS 需直连 `:8090`。

---

## 10. SystemAdmin 全量 API

类：`SystemAdminController` · 前缀 `/api/system` · `@PreAuthorize("hasRole('ADMIN')")`

写操作异步记 `OpLogService.log(operatorId, operatorName, action, request, detail)`。

### 10.1 用户

| 方法 | 路径 | Request Body | 说明 |
|------|------|--------------|------|
| GET | `/users` | — | query: keyword, page, size |
| GET | `/users/stats` | — | UserStats |
| GET | `/users/{id}` | — | 含 dept、roles |
| POST | `/users` | CreateUserRequest | BCrypt 密码 |
| PUT | `/users/{id}` | UpdateUserRequest | |
| PUT | `/users/{id}/roles` | `{roleCodes:[...]}` | |
| DELETE | `/users/{id}` | — | **物理删除** |

**CreateUserRequest**：

```json
{
  "username": "zhangsan",
  "password": "plain-text",
  "realName": "张三",
  "deptId": 1,
  "roleCodes": ["user"]
}
```

**UpdateUserRequest**：`realName`, `deptId`, `enabled`, `roleCodes` 均可选（null 不更新）。

### 10.2 角色

| 方法 | 路径 | Body |
|------|------|------|
| GET | `/roles` | — → `List<RoleDTO>` 含 userCount |
| GET | `/roles/{id}` | — |
| POST | `/roles` | `{code,name,permissionCodes?}` |
| PUT | `/roles/{id}` | `{name?,permissionCodes?}` |
| DELETE | `/roles/{id}` | userCount>0 拒绝 |

### 10.3 权限

| 方法 | 路径 | Body |
|------|------|------|
| GET | `/permissions` | — 全量 |
| POST | `/permissions` | `{code,name}` code 唯一 |

### 10.4 部门

| 方法 | 路径 | Body |
|------|------|------|
| GET | `/depts` | — |
| POST | `/depts` | `{name,parentId?}` name 唯一 |

### 10.5 操作日志

| 方法 | 路径 | Query |
|------|------|-------|
| GET | `/logs` | keyword, action, page, size |

**action 枚举（代码中出现的）**：

`CREATE_USER`, `UPDATE_USER`, `DELETE_USER`, `UPDATE_USER_ROLES`, `CREATE_ROLE`, `UPDATE_ROLE`, `DELETE_ROLE`, `CREATE_PERMISSION`, `CREATE_DEPT`

---

## 11. Service 与 Repository

### 11.1 UserService

| 方法 | 事务 | 说明 |
|------|------|------|
| `listUsers(keyword, page, size)` | 读写 | Page 1 起；keyword 空则 findAll |
| `listDirectoryUsers(deptId)` | readOnly | enabled=true；JOIN FETCH dept+roles |
| `getUser(id)` | 读写 | 40400；touch dept.name 防 lazy 问题 |
| `getUserStats()` | 读写 | total/enabled/admin/disabled |
| `createUser(...)` | 读写 | 用户名唯一；BCrypt |
| `updateUser(...)` | 读写 | 部分更新 |
| `deleteUser(id)` | 读写 | deleteById 物理删 |
| `updateUserRoles(id, codes)` | 读写 | 替换角色集 |
| `resolveRoles(codes)` | — | 不存在 → 40000 |

### 11.2 RoleService

| 方法 | 说明 |
|------|------|
| `listRoles()` | RoleDTO + countByRoleId |
| `createRole / updateRole` | 绑定 permissionCodes |
| `deleteRole(id)` | 有用户 → 40000 |

### 11.3 OpLogService

异步 `Mono.fromRunnable(...).subscribeOn(boundedElastic).then()`，失败不阻塞主响应。

### 11.4 SysUserRepository 自定义 SQL

| 方法 | JPQL 要点 |
|------|-----------|
| `searchUsers` | username OR realName LIKE |
| `countByRoleId` | JOIN roles |
| `countAdmin` | role.code = 'admin' |
| `findDirectoryUsers` | enabled=true, optional deptId, FETCH dept+roles |

### 11.5 SysOpLogRepository

`searchLogs(keyword, action, pageable)` — username/detail LIKE + action 精确匹配，按 createdAt DESC。

### 11.6 ContactDirectoryController

| 端点 | 逻辑 |
|------|------|
| `GET /api/contacts/departments` | 全部部门 id ASC |
| `GET /api/contacts/users?deptId=` | 启用用户摘要 `{id,username,realName,deptId,isAdmin}` |

任意登录用户可访问（非 ADMIN 专属）。

---

## 12. Gateway 路由表

配置：`application.yml` → `spring.cloud.gateway.routes`

### 12.1 已配置 Route

| Route ID | URI | Path |
|----------|-----|------|
| knowledge-ai | `lb://enterprise-knowledge-ai-service` | `/api/kb/**`, `/api/ai-qa/**` |
| collaboration | `lb://enterprise-collaboration-service` | `/api/meetings/**`, `/api/todos/**`, `/api/tasks/**`, `/api/notifications/**`, `/api/chat/**`, `/api/docs/**`, `/api/approvals/**`, `/api/announcements/**`, `/api/intents/**`, `/api/keyword-mappings/**` |
| workbench | `lb://enterprise-workbench-service` | `/api/workbench/**` |

### 12.2 本地处理（无 Route 条目）

| 前缀 | Controller |
|------|------------|
| `/api/auth/**` | AuthController |
| `/api/system/**` | SystemAdminController |
| `/api/contacts/**` | ContactDirectoryController |

### 12.3 未进 Gateway、开发期直连 :8083

| 前缀 | 说明 |
|------|------|
| `/api/kb/agent/**` | Agent SSE / 会话 |
| `/mcp/**` | MCP Tool |

生产需在 Gateway 或 Nginx 补路由。

### 12.4 不经 Gateway 的服务

| 能力 | 地址 |
|------|------|
| WebSocket IM | `ws://host:8090/ws/chat` |
| WebSocket 文档 OT | `ws://host:8090/ws/docs` |

---

## 13. 数据库与种子数据

**Schema**：`enterprise_gateway`  
**DDL + Seed**：`resouces/enterprise_knowledge_workspace.sql`（约 L208–344）

### 13.1 ER 图

```mermaid
erDiagram
    sys_dept ||--o{ sys_user : "dept_id"
    sys_user }o--o{ sys_role : "sys_user_role"
    sys_role }o--o{ sys_permission : "sys_role_permission"
    sys_user ||--o{ sys_op_log : "user_id"
    sys_token_blacklist {
        bigint id PK
        varchar token_hash UK
        timestamp expires_at
    }
    sys_user {
        bigint id PK
        varchar username UK
        varchar password_hash
        bigint dept_id FK
        tinyint enabled
    }
    sys_dept {
        bigint id PK
        varchar name UK
        bigint parent_id
    }
    sys_role {
        bigint id PK
        varchar code UK
        varchar name
    }
    sys_permission {
        bigint id PK
        varchar code UK
        varchar name
    }
    sys_op_log {
        bigint id PK
        bigint user_id
        varchar action
        varchar path
    }
```

### 13.2 表字段摘要

**sys_user**

| 列 | 类型 | 说明 |
|----|------|------|
| id | BIGINT PK | 自增 |
| username | VARCHAR(64) UK | |
| password_hash | VARCHAR(200) | BCrypt |
| real_name | VARCHAR(64) | |
| dept_id | BIGINT FK | 可空 |
| enabled | TINYINT | 1 启用 |
| created_at / updated_at | TIMESTAMP | |

**sys_token_blacklist**

| 列 | 说明 |
|----|------|
| token_hash | SHA-256 hex，UK |
| expires_at | 用于定时清理 |

### 13.3 种子数据速查

**部门**：技术部(1)、产品部(2)、设计部(3)

**用户**（密码 hash 同上，测试环境常用密码 `123456`）：

| id | username | realName | dept | enabled | 角色 |
|----|----------|----------|------|---------|------|
| 1 | admin | 系统管理员 | 1 | ✅ | admin |
| 2 | zhangsan | 张三 | 1 | ✅ | manager |
| 3 | lisi | 李四 | 2 | ✅ | user |
| 4 | wangwu | 王五 | 1 | ❌ | user |
| 5 | zhaoliu | 赵六 | 3 | ✅ | user |

**角色**：admin / manager / user

**权限 code 示例**：`system:user:read`, `kb:document:write`, ...

**角色-权限**：admin 全权限；manager 部分；user 只读为主。

---

## 14. 响应与错误处理

### 14.1 两套异常出口

| 层级 | 处理器 | HTTP 状态 | Body code |
|------|--------|-----------|-----------|
| Security Filter | `ApiResponseWriter` | 401/403 原样 | 40100/40300 |
| 限流 Filter | `ApiResponseWriter` | 429 | 42900 |
| Controller | `GatewayExceptionHandler` | **200** | BizException.code |
| 校验失败 | `GatewayExceptionHandler` | **200** | 40000 |

### 14.2 ErrorCode（frameworks-common）

| 枚举 | 码 | 场景 |
|------|-----|------|
| UNAUTHORIZED | 40100 | 未登录/token 无效 |
| FORBIDDEN | 40300 | 无 ADMIN/IP 拒绝 |
| PARAM_INVALID | 40000 | 校验、业务参数 |
| SYSTEM_ERROR | 50000 | 未捕获（Gateway 少见） |

---

## 15. WebFlux + JPA 编程规范

### 15.1 铁律

**所有 JPA 调用必须在 `Schedulers.boundedElastic()` 上执行。**

```java
return Mono.fromCallable(() -> userRepository.findById(id))
    .subscribeOn(Schedulers.boundedElastic())
    .map(Results::success);
```

### 15.2 为什么

Gateway 运行在 Netty 事件循环线程上。JPA/Hibernate 是阻塞 IO，直接在 Reactor 链主线程调用会阻塞整个 worker，导致吞吐骤降甚至 watchdog 超时。

### 15.3 常见组合模式

| 模式 | 示例位置 |
|------|----------|
| 查询 | `Mono.fromCallable(...).subscribeOn(boundedElastic).map(Results::success)` |
| 写 + 审计 | `.flatMap(saved -> opLogService.log(...).thenReturn(Results.success(saved)))` |
| 纯写 | `Mono.fromRunnable(...).subscribeOn(boundedElastic).thenReturn(...)` |
| JWT 解析 | converter 内 `Mono.fromCallable(() -> jwt.parse).subscribeOn(boundedElastic)` |
| 黑名单 | `TokenBlacklistService` 内 boundedElastic |

### 15.4 UserContext 生命周期

```text
JwtAuthenticationWebFilter 成功 → UserContext.set(UserInfo)
        ↓
SystemAdminController / 日志读取 UserContext.userId()
        ↓
SecurityConfig addFilterAfter → UserContext.clear()
```

---

## 16. 阅读路线与问题反查

| 阶段 | 阅读顺序 |
|------|----------|
| ① | `application.yml` routes + whitelist |
| ② | §5 请求顺序（本文） |
| ③ | `SecurityConfig` → `JwtAuthenticationWebFilter` |
| ④ | GlobalFilter 四个类 |
| ⑤ | `AuthController` → `JwtUtil` → `TokenBlacklistService` |
| ⑥ | `SystemAdminController` → `UserService` |
| ⑦ | `IdentityPropagationGlobalFilter` + 下游 Interceptor |

| 现象 | 排查 |
|------|------|
| login 也 429 | 限流在 Security **之后**，login 也计数；检查 IP 是否 unknown 共用桶 |
| 有 token 仍 401 | 黑名单；exp；secret 不一致；非 compact JWS |
| ADMIN 403 | JWT 无 ROLE_ADMIN；角色 code 是否 admin |
| 下游无 userId | 是否经 Gateway；是否误走 Vite 直连未带头 |
| dept 权限失效 | 前端未传 X-Department-Id |
| 黑名单表膨胀 | 加 @EnableScheduling |
| `/api/system/health` 404 | 白名单配置了但未实现 |

---

## 17. 完整代码地图（逐文件）

### 17.1 启动与配置

| 文件 | 说明 |
|------|------|
| `GatewaySpringbootStarter.java` | 启动入口 |
| `config/AppSecurityProperties.java` | jwt.secret, ttlSeconds, whitelist.paths |
| `config/AppGatewayProperties.java` | ip.blacklist/whitelist, rateLimit.* |

### 17.2 Filter

| 文件 | 说明 |
|------|------|
| `filter/GatewayTraceIdFilter.java` | WebFilter；X-Trace-Id |
| `filter/IpAccessGlobalFilter.java` | IP 403 |
| `filter/SimpleRateLimitGlobalFilter.java` | 429 限流 |
| `filter/JwtAuthenticationWebFilter.java` | JWT 认证成功/失败 |
| `filter/IdentityPropagationGlobalFilter.java` | 下游 Header |

### 17.3 Security

| 文件 | 说明 |
|------|------|
| `security/SecurityConfig.java` | 过滤链总装配 |
| `security/JwtUtil.java` | issueToken / parse |
| `security/RbacUtil.java` | Claims → Authentication |
| `security/TokenBlacklistService.java` | blacklist / isBlacklisted / cleanup |
| `security/UserContext.java` | ThreadLocal 用户上下文 |
| `security/PasswordConfig.java` | BCryptPasswordEncoder @Bean |

### 17.4 Web

| 文件 | 说明 |
|------|------|
| `web/AuthController.java` | login / logout |
| `web/SystemAdminController.java` | RBAC 全套 + logs |
| `web/ContactDirectoryController.java` | departments / users |
| `web/GatewayExceptionHandler.java` | BizException、校验 |

### 17.5 Service

| 文件 | 说明 |
|------|------|
| `service/UserService.java` | 用户 CRUD、stats、通讯录 |
| `service/RoleService.java` | 角色 CRUD |
| `service/RoleDTO.java` | 角色列表 DTO |
| `service/OpLogService.java` | 异步审计 |

### 17.6 Domain

| 文件 | 表 |
|------|-----|
| `domain/SysUser.java` | sys_user |
| `domain/SysDept.java` | sys_dept |
| `domain/SysRole.java` | sys_role |
| `domain/SysPermission.java` | sys_permission |
| `domain/SysOpLog.java` | sys_op_log |
| `domain/TokenBlacklistEntry.java` | sys_token_blacklist |

### 17.7 Repository

| 文件 | 说明 |
|------|------|
| `repository/SysUserRepository.java` | + searchUsers, countAdmin, findDirectoryUsers |
| `repository/SysDeptRepository.java` | findByName |
| `repository/SysRoleRepository.java` | findByCode |
| `repository/SysPermissionRepository.java` | findByCode |
| `repository/SysOpLogRepository.java` | searchLogs |
| `repository/TokenBlacklistRepository.java` | findByTokenHash, deleteByExpiresAtBefore |

### 17.8 其他

| 文件 | 说明 |
|------|------|
| `gateway/response/ApiResponseWriter.java` | Filter JSON 输出 |

---

## 18. 附录 A — REST 接口速查

### Auth `/api/auth`

| 方法 | 路径 | 鉴权 |
|------|------|------|
| POST | `/login` | 白名单 |
| POST | `/logout` | JWT |

### System `/api/system`（ADMIN）

| 资源 | 方法 |
|------|------|
| users | GET/POST `/users`, GET/PUT/DELETE `/users/{id}`, GET `/users/stats`, PUT `/users/{id}/roles` |
| roles | GET/POST `/roles`, GET/PUT/DELETE `/roles/{id}` |
| permissions | GET/POST `/permissions` |
| depts | GET/POST `/depts` |
| logs | GET `/logs` |

### Contacts `/api/contacts`（登录即可）

| 方法 | 路径 |
|------|------|
| GET | `/departments` |
| GET | `/users?deptId=` |

---

## 19. 附录 B — 关键调用链

### 19.1 登录

```
POST /api/auth/login
  AuthController.login
    Mono.fromCallable → SysUserRepository.findByUsername (boundedElastic)
    BCryptPasswordEncoder.matches
    authoritiesOf(user) → List ROLE_* + permission codes
    JwtUtil.issueToken(userId, username, claims)
    Results.success(LoginResponse)
```

### 19.2 带 token 访问下游 KB

```
GET /api/kb/documents + Authorization Bearer
  GatewayTraceIdFilter
  SecurityConfig → jwtAuthenticationConverter
    TokenBlacklistService.isBlacklisted
    JwtUtil.parse → RbacUtil.toAuthentication
  JwtAuthenticationWebFilter 成功 → UserContext
  IpAccessGlobalFilter → SimpleRateLimitGlobalFilter
  IdentityPropagationGlobalFilter
    header X-User-Id, X-Is-Admin; remove Authorization
  Route knowledge-ai → NettyRoutingFilter → :8083
  Knowledge UserContextInterceptor
```

### 19.3 管理员创建用户

```
POST /api/system/users + Bearer (ROLE_ADMIN)
  @PreAuthorize hasRole ADMIN
  UserService.createUser (boundedElastic)
    passwordEncoder.encode
    userRepository.save
  OpLogService.log CREATE_USER (async boundedElastic)
  Results.success(SysUser)
```

### 19.4 退出后再访问

```
POST /api/auth/logout → TokenBlacklistService.blacklist
GET /api/kb/... + 旧 token
  isBlacklisted=true → 401
```

---

## 20. 附录 C — Gotchas 与改进建议

| 项 | 现状 | 影响 | 建议 |
|----|------|------|------|
| Filter 顺序误解 | IP/限流在 JWT **之后** | 未登录请求不会占限流配额 | 文档/监控按真实顺序理解 |
| @EnableScheduling 缺失 | cleanup 不跑 | 黑名单表膨胀 | 启动类加注解 |
| X-Department-Id | 不注入 | KB 部门权限失效 | Filter 写 deptId 或 JWT claim |
| X-Project-Id | 未实现 | 项目级权限无效 | 扩展 claims |
| 物理删用户 | deleteById | 与 AGENTS 软删规范冲突 | 加 deleted 字段 |
| 限流内存 | 多实例独立 | 总阈值 = N × 120 | Redis 集中限流 |
| 代理 IP | remoteAddress | 限流/IP 不准 | X-Forwarded-For |
| 双 sys_user | 两库独立 | 协同账号不同步 | SSO 或同步任务 |
| api.md depts | 文档写 departments | 联调 404 | 以 `/depts` 为准 |
| EAGER roles | 登录/load 拉全权限 | 用户多时慢 | LAZY + DTO |
| agent/mcp 无路由 | 生产经 Gateway 404 | Agent 不可用 | 补 Route |
| UserContext.isAdmin | JWT 成功未 set | 仅 isAdmin() 为空 | 成功回调补 set |
| /api/system/health | 白名单无实现 | 健康探针误配 | 实现或删白名单 |

---

*文档结束 · v3.0 · 2026-05-25*
