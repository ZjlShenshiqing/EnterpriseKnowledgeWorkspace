# enterprise-collaboration-service 服务解析

> 基于 2026-05-12 代码，解析 JWT 用户认证微服务的完整架构、组件职责与数据流。

---

## 目录

1. [系统架构总览](#1-系统架构总览)
2. [启动入口](#2-启动入口)
3. [配置体系](#3-配置体系)
4. [完整业务流程](#4-完整业务流程)
5. [Entity 层](#5-entity-层)
6. [DTO 层](#6-dto-层)
7. [Mapper 层](#7-mapper-层)
8. [Service 层](#8-service-层)
9. [Controller 层](#9-controller-层)
10. [JWT 认证链路](#10-jwt-认证链路)
11. [数据库设计](#11-数据库设计)
12. [完整 API 接口清单](#12-完整-api-接口清单)
13. [目录结构速查](#13-目录结构速查)

---

## 1. 系统架构总览

### 1.1 微服务全景图

```mermaid
graph TB
    subgraph "enterprise-collaboration-service :8090"
        direction TB

        subgraph "Web 层"
            FILTER[JwtAuthFilter<br/>Bearer Token → 请求头]
            AUTH_CTL[AuthController<br/>/api/auth/*]
        end

        subgraph "Service 层"
            LOGIN_SVC[UserLoginService 接口]
            LOGIN_IMPL[UserLoginServiceImpl<br/>BCrypt + JWT + 黑名单]
        end

        subgraph "数据层"
            MYSQL[(MySQL<br/>enterprise_collaboration<br/>sys_user / sys_dept)]
        end

        subgraph "工具层"
            JWT[JwtUtil<br/>HS256 签发/验证]
            WRAPPER[MutableRequestWrapper<br/>自定义请求头注入]
        end
    end

    FRONTEND[Vue 前端 :5173] -->|POST /api/auth/login| AUTH_CTL
    FRONTEND -->|Bearer Token| FILTER
    FILTER -->|X-User-Id X-Is-Admin| AUTH_CTL
    AUTH_CTL --> LOGIN_SVC
    LOGIN_SVC --> LOGIN_IMPL
    LOGIN_IMPL --> MYSQL
    LOGIN_IMPL --> JWT
    FILTER --> JWT
    FILTER --> WRAPPER
```

### 1.2 JWT 认证数据流

```mermaid
sequenceDiagram
    participant U as 用户
    participant CTL as AuthController
    participant SVC as UserLoginServiceImpl
    participant DB as MySQL
    participant JWT as JwtUtil
    participant FILTER as JwtAuthFilter

    Note over U,FILTER: ① 登录流程
    U->>CTL: POST /api/auth/login<br/>{username, password}
    CTL->>SVC: login(req)
    SVC->>DB: SELECT * FROM sys_user WHERE username=?
    DB-->>SVC: SysUser
    SVC->>SVC: BCrypt.matches(password, hash)
    SVC->>JWT: generate({userId, username, isAdmin})
    JWT-->>SVC: accessToken (HS256)
    SVC-->>CTL: UserLoginRespDTO
    CTL-->>U: {accessToken, userId, username...}

    Note over U,FILTER: ② 认证流程（后续请求）
    U->>FILTER: Authorization: Bearer xxx
    FILTER->>JWT: parse(token)
    JWT-->>FILTER: Claims {userId, username, isAdmin}
    FILTER->>FILTER: MutableRequestWrapper<br/>注入 X-User-Id / X-Is-Admin
    FILTER->>CTL: 请求转发（带用户头）
```

---

## 2. 启动入口

### 2.1 CollaborationApplication

**文件路径**：`CollaborationApplication.java`

```java
@SpringBootApplication(scanBasePackages = {"com.zjl.collaboration", "com.zjl.common"})
@MapperScan("com.zjl.collaboration.mapper")
public class CollaborationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CollaborationApplication.class, args);
    }
}
```

| 注解 | 作用 |
|------|------|
| `@SpringBootApplication(scanBasePackages)` | 扫描 `com.zjl.collaboration`（本服务）和 `com.zjl.common`（frameworks 公共组件） |
| `@MapperScan("com.zjl.collaboration.mapper")` | 为 MyBatis-Plus Mapper 接口生成代理 |

---

## 3. 配置体系

### 3.1 application.yml

**文件路径**：`src/main/resources/application.yml`

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/enterprise_collaboration
    username: root
    password: 123456
  sql:
    init:
      mode: always
      schema-locations: classpath:db/schema.sql

server:
  port: 8090

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true

auth:
  jwt:
    secret: enterprise-work-platform-jwt-secret-key-2026
    expiration: 86400000
```

| 配置项 | 值 | 说明 |
|--------|-----|------|
| `server.port` | 8090 | 服务端口 |
| `spring.datasource.url` | `enterprise_collaboration` | 专属数据库 |
| `spring.sql.init.mode` | always | 每次启动执行 schema.sql |
| `auth.jwt.secret` | HS256 密钥 | JWT 签名密钥 |
| `auth.jwt.expiration` | 86400000 (24h) | Token 过期时间（毫秒） |

### 3.2 FilterConfig

**文件路径**：`config/FilterConfig.java`

```java
@Configuration
public class FilterConfig {
    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> reg = new FilterRegistrationBean<>();
        reg.setFilter(filter);
        reg.addUrlPatterns("/api/*");
        reg.setOrder(1);
        return reg;
    }
}
```

注册 `JwtAuthFilter` 拦截 `/api/*` 所有请求。

---


## 4. 完整业务流程

### 4.1 用户登录完整链路

```mermaid
sequenceDiagram
    participant FE as Vue前端 :5173
    participant VITE as Vite代理
    participant CTL as AuthController :8090
    participant SVC as UserLoginServiceImpl
    participant DB as MySQL
    participant JWT as JwtUtil

    FE->>FE: 用户输入用户名密码
    FE->>VITE: POST /api/auth/login
    VITE->>CTL: 转发到 :8090
    CTL->>SVC: login(UserLoginReqDTO)

    Note over SVC: 步骤1: 参数校验
    SVC->>SVC: username/password 非空判断

    Note over SVC: 步骤2: 数据库查询
    SVC->>DB: SELECT * FROM sys_user<br/>WHERE username = ?
    DB-->>SVC: SysUser 或 null

    alt 用户不存在或已禁用
        SVC-->>CTL: throw BizException(UNAUTHORIZED)
        CTL-->>FE: 40100 用户名或密码错误
    end

    Note over SVC: 步骤3: 密码验证
    SVC->>SVC: BCryptPasswordEncoder<br/>.matches(password, hash)

    alt 密码不匹配
        SVC-->>CTL: throw BizException(UNAUTHORIZED)
        CTL-->>FE: 40100 用户名或密码错误
    end

    Note over SVC: 步骤4: JWT 签发
    SVC->>JWT: generate({userId, username, isAdmin})
    JWT->>JWT: Jwts.builder()<br/>.claims(claims)<br/>.expiration(now + 24h)<br/>.signWith(HS256 key)
    JWT-->>SVC: accessToken (eyJhbGci...)

    SVC-->>CTL: UserLoginRespDTO
    CTL-->>FE: Result { data: {userId, username, realName, isAdmin, accessToken} }

    Note over FE: 步骤5: 前端存储
    FE->>FE: localStorage.setItem('user', JSON.stringify(user))
    FE->>FE: localStorage.setItem('token', accessToken)
    FE->>FE: router.push('/') 跳转到工作台
```

### 4.2 请求认证链路（登录后每个请求）

```mermaid
sequenceDiagram
    participant FE as Vue前端
    participant VITE as Vite代理
    participant FILTER as JwtAuthFilter
    participant CTL as AuthController
    participant API as 业务API (knowledge-ai)

    Note over FE: 每个请求都带 Authorization 头

    FE->>VITE: GET /api/kb/documents<br/>Authorization: Bearer eyJhbG...
    VITE->>FILTER: 转发到 :8090

    Note over FILTER: 步骤1: 路径白名单检查
    FILTER->>FILTER: /api/auth/login? → 跳过认证

    Note over FILTER: 步骤2: 提取 Bearer Token
    FILTER->>FILTER: authHeader.startsWith("Bearer ")<br/>token = authHeader.substring(7)

    Note over FILTER: 步骤3: JWT 解析
    FILTER->>FILTER: jwtUtil.parse(token)<br/>→ Claims {userId, username, isAdmin}

    alt Token 过期
        FILTER-->>FE: 401 Token已过期
    else Token 无效
        FILTER-->>FE: 401 Token无效
    end

    Note over FILTER: 步骤4: 注入请求头
    FILTER->>FILTER: MutableRequestWrapper.putHeader<br/>("X-User-Id", userId)<br/>("X-Is-Admin", isAdmin)

    FILTER->>API: 转发到 knowledge-ai :8083<br/>带 X-User-Id / X-Is-Admin 头
    API-->>FILTER: 响应
    FILTER-->>FE: 响应
```

### 4.3 用户注册流程

```mermaid
sequenceDiagram
    participant FE as Vue前端
    participant CTL as AuthController
    participant SVC as UserLoginServiceImpl
    participant DB as MySQL

    FE->>CTL: POST /api/auth/register<br/>{username, password, realName, deptId}

    CTL->>SVC: register(req)
    SVC->>SVC: 参数校验: username/password 非空

    SVC->>DB: SELECT COUNT(1) FROM sys_user<br/>WHERE username = ?
    DB-->>SVC: count

    alt count > 0
        SVC-->>CTL: throw BizException(PARAM_INVALID)
        CTL-->>FE: 40000 用户名已存在
    end

    SVC->>SVC: BCryptPasswordEncoder.encode(password)
    SVC->>DB: INSERT INTO sys_user<br/>(username, password_hash, real_name, dept_id)<br/>VALUES (?, ?, ?, ?)

    Note over SVC: isAdmin 默认 0, enabled 默认 1

    SVC-->>CTL: UserRegisterRespDTO {userId, username}
    CTL-->>FE: 注册成功

    Note over FE: 自动切回登录页<br/>回填用户名
```

### 4.4 用户登出与注销流程

```mermaid
flowchart TD
    A[用户操作] --> B{操作类型}
    B -->|登出| C[POST /api/auth/logout?accessToken=xxx]
    B -->|注销账号| D[POST /api/auth/deletion]

    C --> E["Token 加入黑名单"]
    E --> F[前端清除 localStorage]
    F --> G[跳转到登录页]

    D --> H[参数校验]
    H --> I[查询用户]
    I --> J{用户存在?}
    J -->|否| K[NOT_FOUND]
    J -->|是| L{BCrypt 密码验证}
    L -->|失败| M[PARAM_INVALID]
    L -->|通过| N[物理删除用户]
    N --> O[返回成功]
```

### 4.5 Token 黑名单机制

```mermaid
sequenceDiagram
    participant U as 用户
    participant CTL as AuthController
    participant SVC as UserLoginServiceImpl
    participant BL as tokenBlacklist<br/>(ConcurrentHashMap)
    participant JWT as JwtUtil

    Note over U,JWT: 登出场景
    U->>CTL: POST /api/auth/logout?accessToken=xxx
    CTL->>SVC: logout(accessToken)
    SVC->>BL: add(accessToken)
    Note over BL: Token 加入黑名单<br/>即使未过期也标记失效

    Note over U,JWT: 再次请求场景
    U->>CTL: 请求带已登出的 Token
    CTL->>SVC: checkLogin(accessToken)
    SVC->>BL: contains(accessToken)?
    BL-->>SVC: true (在黑名单中)
    SVC-->>CTL: throw BizException(UNAUTHORIZED, "Token已失效")
    CTL-->>U: 40100

    Note over BL: 黑名单存储在内存<br/>服务重启后清空
    Note over JWT: JWT 本身24h后自然过期
```

---

## 5. Entity 层

### 5.1 SysUser

## 5. Entity 层

### 11.1 SysUser

**文件路径**：`entity/SysUser.java`  
**表名**：`sys_user`  
**主键策略**：`IdType.AUTO`（自增）

| 字段 | 类型 | 数据库列 | 说明 |
|------|------|----------|------|
| `id` | `Long` | `id BIGINT PK AUTO_INCREMENT` | 自增主键 |
| `username` | `String` | `username VARCHAR(64) UNIQUE` | 用户名 |
| `passwordHash` | `String` | `password_hash VARCHAR(200)` | BCrypt 密码哈希 |
| `realName` | `String` | `real_name VARCHAR(64)` | 真实姓名 |
| `deptId` | `Long` | `dept_id BIGINT` | 部门 ID |
| `isAdmin` | `Integer` | `is_admin TINYINT DEFAULT 0` | 是否管理员 |
| `enabled` | `Integer` | `enabled TINYINT DEFAULT 1` | 是否启用 |
| `createdAt` | `LocalDateTime` | `created_at` | 创建时间 |
| `updatedAt` | `LocalDateTime` | `updated_at` | 更新时间 |

---

## 6. DTO 层

```mermaid
classDiagram
    class UserLoginReqDTO {
        +String username
        +String password
    }
    class UserLoginRespDTO {
        +Long userId
        +String username
        +String realName
        +Boolean isAdmin
        +Long deptId
        +String accessToken
    }
    class UserRegisterReqDTO {
        +String username
        +String password
        +String realName
        +Long deptId
    }
    class UserRegisterRespDTO {
        +Long userId
        +String username
    }
    class UserDeletionReqDTO {
        +String username
        +String password
    }

    UserLoginReqDTO --> UserLoginRespDTO : login()
    UserRegisterReqDTO --> UserRegisterRespDTO : register()
    UserDeletionReqDTO --> UserLoginRespDTO : deletion()
```

### 11.1 DTO 清单

| DTO | 字段 | 用途 |
|-----|------|------|
| `UserLoginReqDTO` | username, password | 登录请求 |
| `UserLoginRespDTO` | userId, username, realName, isAdmin, deptId, accessToken | 登录/checkLogin 响应 |
| `UserRegisterReqDTO` | username, password, realName, deptId | 注册请求 |
| `UserRegisterRespDTO` | userId, username | 注册响应 |
| `UserDeletionReqDTO` | username, password | 注销请求 |

---

## 7. Mapper 层

### 11.1 SysUserMapper

**文件路径**：`mapper/SysUserMapper.java`

```java
@Mapper
public interface SysUserMapper extends BaseMapper<SysUser> {
}
```

继承 `BaseMapper<SysUser>`，获得 MyBatis-Plus 内置的 insert / updateById / deleteById / selectById / selectOne / selectCount 等方法。

---

## 8. Service 层

### 11.1 UserLoginService 接口

**文件路径**：`service/UserLoginService.java`

```java
public interface UserLoginService {
    UserLoginRespDTO login(UserLoginReqDTO requestParam);        // 登录
    UserLoginRespDTO checkLogin(String accessToken);              // Token 验证
    void logout(String accessToken);                              // 登出
    Boolean hasUserName(String username);                         // 用户名检查
    UserRegisterRespDTO register(UserRegisterReqDTO requestParam); // 注册
    void deletion(UserDeletionReqDTO requestParam);               // 注销用户
}
```

### 8.2 UserLoginServiceImpl 详解

**文件路径**：`service/impl/UserLoginServiceImpl.java`  
**依赖**：`SysUserMapper` + `JwtUtil` + `BCryptPasswordEncoder`

#### login() 流程

```
login(req)
  ├── 参数校验：username/password 非空
  ├── 查库：SELECT * FROM sys_user WHERE username=?
  ├── 状态检查：enabled == 0 → UNAUTHORIZED
  ├── 密码校验：BCryptPasswordEncoder.matches(password, hash)
  │     └── 不匹配 → UNAUTHORIZED
  ├── 构造 JWT Claims：{userId, username, isAdmin}
  ├── jwtUtil.generate(claims) → accessToken
  └── 返回 UserLoginRespDTO
```

#### checkLogin() 流程

```
checkLogin(accessToken)
  ├── 空值检查
  ├── 黑名单检查：tokenBlacklist.contains(token) → UNAUTHORIZED
  ├── jwtUtil.parse(token) → Claims
  ├── 查库：selectById(userId)
  │     └── 不存在或 disabled → UNAUTHORIZED
  └── 返回 UserLoginRespDTO（不含 token）
```

#### register() 流程

```
register(req)
  ├── 参数校验：username/password 非空
  ├── 用户名唯一性检查：hasUserName()
  ├── BCryptPasswordEncoder.encode(password)
  ├── INSERT sys_user (isAdmin=0, enabled=1)
  └── 返回 UserRegisterRespDTO {userId, username}
```

#### logout() 与 deletion()

```
logout(accessToken)
  └── tokenBlacklist.add(accessToken)   // 内存黑名单

deletion(req)
  ├── 用户名密码校验
  ├── sysUserMapper.deleteById(id)      // 物理删除
  └── @Transactional 保护
```

---

## 9. Controller 层

### 11.1 AuthController

**文件路径**：`web/AuthController.java`  
**路径前缀**：`/api/auth`  
**依赖**：`UserLoginService`

| 方法 | 路径 | 请求体 | 响应 | 说明 |
|------|------|--------|------|------|
| `POST` | `/login` | `UserLoginReqDTO` | `Result<UserLoginRespDTO>` | 登录，返回 JWT |
| `GET` | `/check-login` | Query: `accessToken` | `Result<UserLoginRespDTO>` | 验证 token 有效性 |
| `POST` | `/logout` | Query: `accessToken` | `Result<Void>` | 登出，token 加黑名单 |
| `GET` | `/has-username` | Query: `username` | `Result<Boolean>` | 用户名是否存在 |
| `POST` | `/register` | `UserRegisterReqDTO` | `Result<UserRegisterRespDTO>` | 注册新用户 |
| `POST` | `/deletion` | `UserDeletionReqDTO` | `Result<Void>` | 注销用户 |

---

## 10. JWT 认证链路

### 11.1 JwtUtil

**文件路径**：`util/JwtUtil.java`

```java
@Component
public class JwtUtil {
    private final SecretKey key;        // HS256 密钥
    private final long expiration;      // 24h

    public JwtUtil(@Value("${auth.jwt.secret}") String secret,
                   @Value("${auth.jwt.expiration}") long expiration) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = expiration;
    }

    public String generate(Map<String, Object> claims) {
        return Jwts.builder()
                .claims(claims)                              // {userId, username, isAdmin}
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + expiration))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        return Jwts.parser().verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
    }
}
```

### 11.2 JwtAuthFilter

**文件路径**：`web/JwtAuthFilter.java`

```mermaid
flowchart TD
    A[请求进入] --> B{路径是 /api/auth/login?}
    B -->|是| C[放行，不校验 Token]
    B -->|否| D{Authorization: Bearer xxx?}
    D -->|否| E[放行，无用户头]
    D -->|是| F[截取 Token]
    F --> G{parse(token) 成功?}
    G -->|过期| H[401 Token已过期]
    G -->|无效| I[401 Token无效]
    G -->|成功| J[提取 Claims]
    J --> K[MutableRequestWrapper 注入:<br/>X-User-Id / X-Department-Id / X-Is-Admin]
    K --> L[chain.doFilter]
```

### 11.3 MutableRequestWrapper

**文件路径**：`web/MutableRequestWrapper.java`

继承 `HttpServletRequestWrapper`，允许向请求中添加自定义 Header。解决 `HttpServletRequest` 不允许修改 Header 的限制。

```java
public class MutableRequestWrapper extends HttpServletRequestWrapper {
    private final Map<String, String> customHeaders = new HashMap<>();

    public void putHeader(String name, String value) {
        customHeaders.put(name, value);
    }

    @Override
    public String getHeader(String name) {
        return customHeaders.getOrDefault(name, super.getHeader(name));
    }
}
```

---

## 11. 数据库设计

### 11.1 ER 图

```mermaid
erDiagram
    sys_dept ||--o{ sys_user : dept_id

    sys_dept {
        BIGINT id PK "自增"
        VARCHAR name "部门名(唯一)"
        BIGINT parent_id "父部门"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }

    sys_user {
        BIGINT id PK "自增"
        VARCHAR username UK "用户名(唯一)"
        VARCHAR password_hash "BCrypt哈希"
        VARCHAR real_name "真实姓名"
        BIGINT dept_id FK "部门"
        TINYINT is_admin "管理员 0/1"
        TINYINT enabled "启用 0/1"
        TIMESTAMP created_at
        TIMESTAMP updated_at
    }
```

### 11.2 种子数据

| 用户名 | 密码 | 角色 |
|--------|------|------|
| `admin` | `123456` (BCrypt) | 管理员 |
| `zhangsan` | `123456` | 普通用户 |
| `lisi` | `123456` | 普通用户 |

部门：`技术部`、`产品部`、`设计部`

---

## 12. 完整 API 接口清单

| # | 方法 | 路径 | 参数 | 响应 | 认证 |
|---|------|------|------|------|------|
| 1 | `POST` | `/api/auth/login` | `{username, password}` | `{userId, username, realName, isAdmin, deptId, accessToken}` | 否 |
| 2 | `GET` | `/api/auth/check-login` | Query: `accessToken` | `{userId, username, realName, isAdmin, deptId}` | 否 |
| 3 | `POST` | `/api/auth/logout` | Query: `accessToken` | `null` | 否 |
| 4 | `GET` | `/api/auth/has-username` | Query: `username` | `true/false` | 否 |
| 5 | `POST` | `/api/auth/register` | `{username, password, realName, deptId}` | `{userId, username}` | 否 |
| 6 | `POST` | `/api/auth/deletion` | `{username, password}` | `null` | 否 |

---

## 13. 目录结构速查

```
enterprise-collaboration-service/
├── pom.xml
└── src/main/
    ├── java/com/zjl/collaboration/
    │   ├── CollaborationApplication.java       # 启动类
    │   ├── config/
    │   │   └── FilterConfig.java               # JWT 过滤器注册
    │   ├── dto/                                 # DTO（5个文件）
    │   │   ├── UserLoginReqDTO.java             # 登录入参
    │   │   ├── UserLoginRespDTO.java            # 登录出参
    │   │   ├── UserRegisterReqDTO.java          # 注册入参
    │   │   ├── UserRegisterRespDTO.java         # 注册出参
    │   │   └── UserDeletionReqDTO.java          # 注销入参
    │   ├── entity/
    │   │   └── SysUser.java                     # 用户实体
    │   ├── mapper/
    │   │   └── SysUserMapper.java               # extends BaseMapper
    │   ├── service/
    │   │   ├── UserLoginService.java            # 接口（6个方法）
    │   │   └── impl/
    │   │       └── UserLoginServiceImpl.java    # BCrypt + JWT + 黑名单
    │   ├── util/
    │   │   └── JwtUtil.java                     # HS256 JWT 工具
    │   └── web/
    │       ├── AuthController.java              # /api/auth/*
    │       ├── JwtAuthFilter.java               # Bearer Token 过滤器
    │       └── MutableRequestWrapper.java       # 请求头注入包装器
    └── resources/
        ├── application.yml                      # 端口 8090 + JWT 配置
        └── db/
            └── schema.sql                       # sys_user + sys_dept + 种子数据
```

---

**文档版本**：v1.1（含完整业务流程）  
**最后更新**：2026-05-12  
**覆盖范围**：14 个 Java 源文件 + 2 个资源文件  
**图表数量**：10 个 Mermaid 图表（架构图 ×2、时序图 ×5、流程图 ×2、类图 ×1、ER 图 ×1）
