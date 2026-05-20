# Enterprise Gateway Service 详细设计文档

<img src="assets/ChatGPT Image 2026年5月16日 18_20_21.png" alt="ChatGPT Image 2026年5月16日 18_20_21" style="zoom: 50%;" />

![ChatGPT Image 2026年5月16日 18_23_29](assets/ChatGPT Image 2026年5月16日 18_23_29.png)

## 1. 服务定位

`enterprise-gateway-service` 是整个企业知识工作平台的**统一入口**，承担以下职责：

- **API 网关**：路由转发，将请求分发到下游微服务（知识服务、协作服务、工作台服务）
- **统一认证**：JWT 签发 / 校验 / 黑名单管理
- **访问控制**：IP 黑白名单、RBAC 权限校验
- **流量防护**：基于固定窗口的简易限流
- **全链路追踪**：TraceId 生成与透传
- **操作审计**：管理操作日志异步写入

---

## 2. 项目结构

```
enterprise-gateway-service/src/main/java/com/zjl/
├── GatewaySpringbootStarter.java          # 启动入口
├── config/
│   ├── AppSecurityProperties.java         # JWT / 白名单配置
│   └── AppGatewayProperties.java          # IP黑白名单 / 限流配置
├── filter/
│   ├── GatewayTraceIdFilter.java          # TraceId 生成与透传（最先执行）
│   ├── IpAccessGlobalFilter.java          # IP 黑白名单过滤（Order=-100）
│   ├── SimpleRateLimitGlobalFilter.java   # 简易限流（Order=-90）
│   └── JwtAuthenticationWebFilter.java    # JWT 认证过滤器
├── security/
│   ├── SecurityConfig.java                # Spring Security 核心配置
│   ├── JwtUtil.java                       # JWT 签发与解析
│   ├── RbacUtil.java                      # Claims → Authentication 映射
│   ├── UserContext.java                   # 请求级用户上下文（ThreadLocal）
│   ├── PasswordConfig.java                # BCrypt 密码编码器
│   └── TokenBlacklistService.java         # Token 黑名单（登出失效）
├── web/
│   ├── AuthController.java                # 登录 / 退出接口
│   └── SystemAdminController.java         # RBAC 管理后台（管理员专用）
├── service/
│   └── OpLogService.java                  # 操作日志异步写入
├── domain/                                # JPA 实体
│   ├── SysUser.java
│   ├── SysRole.java
│   ├── SysPermission.java
│   ├── SysDept.java
│   ├── SysOpLog.java
│   └── TokenBlacklistEntry.java
├── repository/                            # JPA 仓库
│   └── ...
└── common/response/
    └── ApiResponseWriter.java             # 统一 JSON 响应输出器
```

---

## 3. 请求处理完整链路

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant TraceFilter as TraceId 过滤器
    participant IpFilter as IP 访问控制
    participant RateLimiter as 限流过滤器
    participant Security as Spring Security 过滤链
    participant JWTFilter as JWT 认证过滤器
    participant Blacklist as Token 黑名单
    participant JwtUtil as JWT 解析
    participant RbacUtil as RBAC 权限映射
    participant UserCtx as UserContext
    participant Router as 路由转发
    participant Downstream as 下游微服务

    Client->>TraceFilter: HTTP 请求
    Note over TraceFilter: Order = 最高优先级

    TraceFilter->>TraceFilter: 1. 读取请求头 X-Trace-Id
    TraceFilter->>TraceFilter: 2. 无则生成 UUID
    TraceFilter->>TraceFilter: 3. 放入 MDC
    TraceFilter->>TraceFilter: 4. 写入响应头 X-Trace-Id

    TraceFilter->>IpFilter: 传递
    Note over IpFilter: Order = -100

    IpFilter->>IpFilter: 获取客户端 IP
    alt 白名单开启 且 IP 不在白名单
        IpFilter-->>Client: 403 IP 未在白名单内
    else 黑名单命中
        IpFilter-->>Client: 403 IP 已被禁止访问
    else 通过
        IpFilter->>RateLimiter: 传递
    end

    Note over RateLimiter: Order = -90

    RateLimiter->>RateLimiter: 固定窗口计数
    alt 超出阈值
        RateLimiter-->>Client: 429 请求过于频繁
    else 未超限
        RateLimiter->>Security: 传递
    end

    Note over Security: SecurityWebFilterChain

    Security->>Security: CSRF 关闭 / CORS 默认
    Security->>Security: 白名单路径匹配？

    alt 白名单路径（登录 / 健康检查等）
        Security->>Router: 直接放行
        Router->>Downstream: 按路由规则转发
    else 非白名单，需要认证
        Security->>JWTFilter: 进入 JWT 认证

        JWTFilter->>JWTFilter: 提取 Authorization 头
        alt 无头 / 非 Bearer 格式
            JWTFilter-->>Client: 401 Unauthorized
        else 格式正确
            JWTFilter->>JWTFilter: 截取 token（去 "Bearer " 前缀）
            JWTFilter->>Blacklist: token 是否已拉黑？

            alt 已拉黑
                Blacklist-->>Client: 401 token revoked
            else 正常
                JWTFilter->>JwtUtil: 解析 JWT 验签 + 过期校验

                alt 签名无效 / 已过期
                    JwtUtil-->>Client: 401 Unauthorized
                else 解析成功
                    JwtUtil-->>RbacUtil: Claims (userId, username, authorities)
                    RbacUtil->>RbacUtil: userId ← sub
                    RbacUtil->>RbacUtil: username ← claim
                    RbacUtil->>RbacUtil: authorities ← GrantedAuthority 列表

                    RbacUtil-->>JWTFilter: UsernamePasswordAuthenticationToken
                    JWTFilter->>UserCtx: set(userId, username, authorities)
                    JWTFilter->>Security: 标记 authenticated=true

                    Security->>Security: @PreAuthorize 注解鉴权

                    alt 无权限
                        Security-->>Client: 403 Forbidden
                    else 有权限
                        Security->>Router: 放行
                        Router->>Downstream: 按路由规则转发
                        Downstream-->>Client: 业务响应
                    end
                end
            end
        end
    end

    Note over TraceFilter: doFinally → MDC.remove(traceId)
    Note over Security: doFinally → UserContext.clear()
```

---

## 4. 各层详解

### 4.1 TraceId 过滤器（GatewayTraceIdFilter）

```mermaid
flowchart LR
    A[请求进入] --> B{请求头有无 X-Trace-Id?}
    B -->|有| C[复用上游 traceId]
    B -->|无| D[UUID 生成新 traceId]
    C --> E[放入 MDC]
    D --> E
    E --> F[写入响应头 X-Trace-Id]
    F --> G[继续过滤链]
    G --> H[请求结束 doFinally]
    H --> I[MDC.remove traceId]
```

**关键设计**：
- 实现了 `WebFilter` 接口（非 `GlobalFilter`），优先级最高，最先执行
- 请求头透传机制：如果上游（Nginx、前端）已传 traceId，直接复用，保证全链路串联
- 响应也带 traceId，排查问题时前端可直接拿到
- `doFinally` 清理 MDC，防止线程复用时 traceId 串用
- 日志配置 `%X{traceId}` 让每条日志自动带上 traceId

### 4.2 IP 访问控制（IpAccessGlobalFilter）

```mermaid
flowchart TD
    A[获取客户端 IP] --> B{白名单是否配置?}
    B -->|是| C{IP 在白名单中?}
    C -->|否| D[403 IP 未在白名单内]
    C -->|是| E{黑名单是否配置?}
    B -->|否| E
    E -->|是| F{IP 在黑名单中?}
    F -->|是| G[403 IP 已被禁止访问]
    F -->|否| H[放行]
    E -->|否| H
```

**关键设计**：
- `Order = -100`，在限流和认证之前执行
- 白名单优先：若配置了白名单，非白名单 IP 直接拒绝（黑名单不再检查）
- 从 `remoteAddress` 获取 IP；若部署在反向代理后需要改为解析 `X-Forwarded-For`
- 均通过 `ApiResponseWriter` 返回统一 JSON

### 4.3 简易限流（SimpleRateLimitGlobalFilter）

```mermaid
flowchart TD
    A[限流是否启用?] -->|否| B[直接放行]
    A -->|是| C[取客户端 IP 作为 key]
    C --> D[计算当前窗口起点 windowStart]
    D --> E["ConcurrentHashMap.compute 原子更新计数"]
    E --> F{count > limit?}
    F -->|是| G[429 请求过于频繁]
    F -->|否| H[放行]
```

**关键设计**：
- `Order = -90`，在 IP 过滤之后、认证之前执行
- 算法：**固定窗口计数**，按 IP 维度统计，窗口大小和阈值可配置
- 使用 `ConcurrentHashMap.compute` 原子更新，无锁线程安全
- MVP 阶段使用内存计数器，生产环境建议替换为 Redis 集中式限流
- 当前默认：60 秒窗口内最多 120 次请求

### 4.4 Spring Security 过滤链（SecurityConfig）

```mermaid
flowchart TD
    subgraph SecurityWebFilterChain
        A[csrf: disable] --> B[cors: defaults]
        B --> C[securityContextRepo: NoOp 无状态]
        C --> D[httpBasic/formLogin: disable]
        D --> E[请求结束 → UserContext.clear]
        E --> F{URL 匹配?}
        F -->|白名单| G[permitAll 放行]
        F -->|其他| H[需要认证]
        H --> I[JWT 认证过滤器]
        I -->|成功| J[@PreAuthorize 权限校验]
        J -->|有权限| K[转发下游]
        I -->|失败| L[401 统一 JSON]
        J -->|无权限| M[403 统一 JSON]
    end
```

**关键设计**：
- **无状态模式**：`NoOpServerSecurityContextRepository`，不依赖 Session
- **关闭 CSRF**：前后端分离 + JWT，无 Cookie-Session 机制
- **白名单**：登录接口 `/api/auth/login`、健康检查等直接放行
- **JWT 过滤器**：在 `AUTHENTICATION` 位置替换默认的表单认证
- **异常统一处理**：401 和 403 通过 `ApiResponseWriter` 返回 JSON，而非默认的重定向页面
- **UserContext 清理**：`doFinally` 确保每次请求结束清空 ThreadLocal

### 4.5 JWT 认证过滤器（JwtAuthenticationWebFilter）

```mermaid
flowchart TD
    A[继承 AuthenticationWebFilter] --> B[构造器注入 converter + failureHandler]
    B --> C[设置认证转换器]
    C --> D[设置失败处理器]
    D --> E[设置成功处理器]
    E --> F["成功: setUserContext → 继续过滤链"]
    F --> G["noopAuthenticationManager: 透传式认证管理器，标记 authenticated=true"]
```

**关键设计**：
- 继承 Spring Security 的 `AuthenticationWebFilter`，只负责**组装流程**
- 真正的 JWT 解析逻辑在 `ServerAuthenticationConverter`（定义在 SecurityConfig 中）
- 使用"透传式"认证管理器：converter 已完成校验，不再二次认证
- 认证成功后将用户信息写入 `UserContext`，供业务代码使用

### 4.6 JWT 认证转换器（jwtAuthenticationConverter）

```mermaid
flowchart TD
    A[读取 Authorization 头] --> B{格式为 Bearer xxx?}
    B -->|否| C[返回 Mono.empty]
    B -->|是| D[截取 token]
    D --> E["黑名单校验 isBlacklisted"]
    E --> F{是否已拉黑?}
    F -->|是| G["Mono.error token revoked"]
    F -->|否| H["jwtService.parse 解析 JWT"]
    H --> I["rbacService.toAuthentication 映射权限"]
    I --> J[返回 Authentication]
```

**完整转换链路**：
```
HTTP Request → 提取 Bearer token → 黑名单校验 → JWT 解析 → RBAC 权限映射 → Authentication
```

### 4.7 RBAC 权限映射（RbacUtil）

```mermaid
flowchart LR
    A["Claims"] --> B["userId = sub"]
    A --> C["username = claim"]
    A --> D["authorities = claim"]
    D --> E["List<SimpleGrantedAuthority>"]
    B --> F["UsernamePasswordAuthenticationToken"]
    C --> F
    E --> F
    F --> G["setDetails(claims)"]
    G --> H["setAuthenticated(true)"]
    H --> I["Mono.just(auth)"]
```

**JWT Claims 约定**：

| Claim | 含义 | 示例 |
|-------|------|------|
| `sub` | 用户 ID | `1` |
| `username` | 用户名 | `admin` |
| `authorities` | 权限列表 | `["ROLE_ADMIN", "PERM_doc_delete"]` |

### 4.8 UserContext（请求级用户上下文）

```mermaid
flowchart TD
    A["JWT 认证成功"] --> B["UserContext.set(userInfo)"]
    B --> C["ThreadLocal: userId"]
    B --> D["ThreadLocal: username"]
    B --> E["ThreadLocal: authorities"]
    C --> F["业务代码: UserContext.userId()"]
    D --> G["业务代码: UserContext.username()"]
    E --> H["业务代码检查权限"]
    I["请求结束 doFinally"] --> J["UserContext.clear()"]
    J --> K["ThreadLocal.remove 全部"]
```

**为什么用 ThreadLocal**：
- 避免层层传参：下游任何地方直接 `UserContext.userId()` 即可获取当前用户
- `doFinally` 确保每次请求结束自动清理，防止内存泄漏和上下文串用

---

## 5. 认证模块详解

### 5.1 登录流程

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant AuthCtrl as AuthController
    participant UserRepo as SysUserRepository
    participant BCrypt as BCryptPasswordEncoder
    participant JwtUtil as JWT 服务

    Client->>AuthCtrl: POST /api/auth/login<br/>{username, password}

    AuthCtrl->>UserRepo: findByUsername(username)
    Note over UserRepo: subscribeOn boundedElastic<br/>避免阻塞 Netty IO 线程
    UserRepo-->>AuthCtrl: SysUser or null

    alt 用户不存在或被禁用
        AuthCtrl-->>Client: 401 用户名或密码错误
    else 用户存在
        AuthCtrl->>BCrypt: matches(明文, 密文)
        alt 密码不匹配
            AuthCtrl-->>Client: 401 用户名或密码错误
        else 密码匹配
            AuthCtrl->>AuthCtrl: 从角色+权限拼装 authorities
            AuthCtrl->>JwtUtil: issueToken(userId, username, claims)
            JwtUtil-->>AuthCtrl: JWT 字符串
            AuthCtrl-->>Client: 200 { token: "xxx" }
        end
    end
```

**authorities 组装规则**（`authoritiesOf` 方法）：

```
用户拥有的角色 → ROLE_admin, ROLE_user
用户拥有的权限 → PERM_doc_delete, PERM_user_create
最终 JWT 中存储完整的角色 + 权限列表
```

**安全设计**：
- 不区分"用户不存在"和"密码错误"，统一返回相同错误信息，防止用户名枚举攻击
- BCrypt 加密存储，不可逆
- JWT 有效期可配置（默认 7200 秒）

### 5.2 退出流程（Token 黑名单）

```mermaid
sequenceDiagram
    participant Client as 客户端
    participant AuthCtrl as AuthController
    participant Blacklist as TokenBlacklistService
    participant JwtUtil as JWT 解析
    participant DB as 数据库

    Client->>AuthCtrl: POST /api/auth/logout<br/>Header: Authorization: Bearer xxx

    AuthCtrl->>AuthCtrl: 提取 Bearer token
    AuthCtrl->>Blacklist: blacklist(token)

    Blacklist->>JwtUtil: parse(token) 获取过期时间
    JwtUtil-->>Blacklist: exp (过期时间戳)

    Blacklist->>Blacklist: SHA-256(token) 哈希存储
    Blacklist->>DB: save(tokenHash, expiresAt)

    Blacklist-->>AuthCtrl: 完成
    AuthCtrl-->>Client: 200 退出成功
```

**为什么不用 JWT 自身的过期机制而要加黑名单？**

JWT 的过期时间是签发时固定的，假设签发时设了 2 小时有效期，用户在第 1 小时点击退出，剩余的 1 小时内 token 仍然有效。黑名单解决了这个问题——退出后立即让 token 失效。

**安全细节**：
- 数据库只存 SHA-256 哈希，不存原文，防止数据库泄露时 token 被窃取
- 记录 `expiresAt`，定时任务每 60 秒清理已过期条目
- 退出接口幂等设计：无 token 也返回成功

### 5.3 管理后台（SystemAdminController）

```mermaid
flowchart TD
    subgraph RBAC 管理接口
        A["@PreAuthorize(hasRole('ADMIN'))"]
        A --> B["GET /api/system/users 用户列表"]
        A --> C["POST /api/system/users 创建用户"]
        A --> D["PUT /api/system/users/{id}/roles 更新角色"]
        A --> E["GET /api/system/roles 角色列表"]
        A --> F["POST /api/system/roles 创建角色"]
        A --> G["GET /api/system/permissions 权限列表"]
        A --> H["POST /api/system/permissions 创建权限"]
        A --> I["GET /api/system/depts 部门列表"]
        A --> J["POST /api/system/depts 创建部门"]
    end
    B --> K["OpLogService.log 异步审计日志"]
    C --> K
    D --> K
    F --> K
    H --> K
    J --> K
```

**RBAC 数据模型**：

```
SysUser ──多对多── SysRole ──多对多── SysPermission
   │                    │
   └── deptId           └── code (ROLE_ADMIN / ROLE_USER)
   
SysDept (树形结构, parentId)
```

**角色编码约定**：
- `admin` → JWT 中存为 `ROLE_admin`
- `user` → JWT 中存为 `ROLE_user`

**权限编码约定**（直接放入 JWT）：
- `user:create` → `PERM_user_create`
- `doc:delete` → `PERM_doc_delete`

---

## 6. 配置详解

### 6.1 路由配置

```yaml
spring.cloud.gateway.routes:
  - id: knowledge-ai           # 知识库与问答服务
    uri: http://localhost:8081
    predicates:
      - Path=/api/kb/**,/api/ai-qa/**
      
  - id: collaboration          # 协同业务服务
    uri: http://localhost:8090
    predicates:
      - Path=/api/meetings/**,/api/todos/**,/api/tasks/**,/api/notifications/**
      
  - id: workbench              # 工作台聚合服务
    uri: http://localhost:8083
    predicates:
      - Path=/api/workbench/**
```

```mermaid
flowchart LR
    Client --> Gateway["Gateway :8086"]
    Gateway -->|"/api/kb/**"| Knowledge["Knowledge-AI :8081"]
    Gateway -->|"/api/meetings/**"| Collab["Collaboration :8090"]
    Gateway -->|"/api/todos/**"| Collab
    Gateway -->|"/api/tasks/**"| Collab
    Gateway -->|"/api/workbench/**"| Workbench["Workbench :8083"]
```

### 6.2 安全配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `app.security.jwt.secret` | HS256 签名密钥（生产应改为安全存储） | dev 占位值 |
| `app.security.jwt.ttl-seconds` | JWT 有效期（秒） | 7200（2 小时） |
| `app.security.whitelist.paths` | 无需认证的路径 | `/api/auth/login`, `/actuator/health` |

### 6.3 网关配置

| 配置项 | 说明 | 默认值 |
|--------|------|--------|
| `app.gateway.ip.blacklist` | IP 黑名单 | `[]` |
| `app.gateway.ip.whitelist` | IP 白名单（非空则只允许白名单） | `[]` |
| `app.gateway.rateLimit.enabled` | 是否启用限流 | `true` |
| `app.gateway.rateLimit.requests` | 窗口内最大请求数 | 120 |
| `app.gateway.rateLimit.windowSeconds` | 时间窗口（秒） | 60 |

### 6.4 数据库

网关有自己的数据库 `enterprise_gateway`，用于存储：
- RBAC 数据：用户、角色、权限、部门
- Token 黑名单：登出后的 token 哈希
- 操作日志：管理操作的审计记录

**注意**：`ddl-auto: none`，依赖手工执行 SQL 脚本建表。

---

## 7. 整体架构图

```mermaid
graph TB
    subgraph "客户端层"
        Browser["浏览器 / 前端"]
        ThirdParty["第三方系统"]
    end

    subgraph "Gateway Service :8086"
        direction TB
        
        subgraph "过滤器链（按 Order 执行）"
            TraceFilter["TraceId 过滤器<br/>WebFilter 最高优先级"]
            IpFilter["IP 访问控制<br/>GlobalFilter Order=-100"]
            RateLimiter["简易限流<br/>GlobalFilter Order=-90"]
        end

        subgraph "Spring Security"
            SecurityChain["SecurityWebFilterChain"]
            JWTFilter["JWT 认证过滤器"]
            Converter["Token → Authentication 转换器"]
            Rbac["RBAC 权限映射"]
            Blacklist["Token 黑名单"]
        end

        subgraph "业务接口"
            AuthController["AuthController<br/>登录 / 退出"]
            AdminController["SystemAdminController<br/>RBAC 管理"]
        end

        subgraph "基础设施"
            UserCtx["UserContext<br/>ThreadLocal 用户上下文"]
            OpLog["OpLogService<br/>操作日志异步写入"]
            ApiWriter["ApiResponseWriter<br/>统一 JSON 响应"]
        end

        subgraph "数据层"
            MySQL["MySQL<br/>enterprise_gateway"]
        end

        TraceFilter --> IpFilter
        IpFilter --> RateLimiter
        RateLimiter --> SecurityChain
        SecurityChain --> JWTFilter
        JWTFilter --> Converter
        Converter --> Rbac
        Converter --> Blacklist
        SecurityChain --> AuthController
        SecurityChain --> AdminController
        AuthController --> UserCtx
        AdminController --> UserCtx
        AdminController --> OpLog
        Blacklist --> MySQL
        OpLog --> MySQL
        AuthController --> MySQL
        AdminController --> MySQL
    end

    subgraph "下游微服务"
        Knowledge["Knowledge-AI Service<br/>:8081<br/>知识库 / AI 问答"]
        Collab["Collaboration Service<br/>:8090<br/>会议 / 待办 / 任务"]
        Workbench["Workbench Service<br/>:8083<br/>工作台聚合"]
    end

    Browser -->|"HTTP 请求"| TraceFilter
    ThirdParty -->|"HTTP 请求"| TraceFilter
    SecurityChain -->|"路由转发"| Knowledge
    SecurityChain -->|"路由转发"| Collab
    SecurityChain -->|"路由转发"| Workbench
```

---

## 8. 请求处理完整时序总结

```
请求进入
  │
  ├─ 1. TraceId 过滤器
  │     └─ 生成/复用 traceId → 放入 MDC → 写入响应头
  │
  ├─ 2. IP 访问控制（Order=-100）
  │     └─ 白名单校验 → 黑名单校验 → 放行/403
  │
  ├─ 3. 简易限流（Order=-90）
  │     └─ 固定窗口计数 → 超限则 429
  │
  ├─ 4. Spring Security 过滤链
  │     ├─ 4.1 白名单匹配 → 直接放行
  │     └─ 4.2 JWT 认证
  │           ├─ 提取 Bearer token
  │           ├─ 黑名单校验 → 已拉黑则 401
  │           ├─ JWT 验签 + 过期校验 → 无效则 401
  │           ├─ Claims → RBAC 权限映射 → Authentication
  │           └─ UserContext.set(用户信息)
  │
  ├─ 5. @PreAuthorize 权限校验 → 无权限则 403
  │
  ├─ 6. 路由转发到下游微服务
  │
  └─ 7. 清理
        ├─ UserContext.clear()
        └─ MDC.remove(traceId)
```
