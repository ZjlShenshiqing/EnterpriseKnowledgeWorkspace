# 认证体系统一与细粒度 RBAC 设计

## 目标

1. 消除协作服务独立的 JWT 认证体系，统一走网关 Sa-Token
2. 在网关层实现细粒度权限码级别的 RBAC 鉴权

## 现状

```
网关 (:8086)  Sa-Token + Redis    sys_user / sys_role / sys_permission (JPA)
协作 (:8090)  JWT + 内存黑名单     自己的 sys_user 表 (MyBatis-Plus)，无 RBAC
前端直连 :8090 的 /ws/chat、/ws/docs WebSocket
```

两套用户表互不通用，协作服务只有 `is_admin` 字段没有角色权限。

## 改后架构

```
前端 → :8086 网关 (Sa-Token 鉴权 + RBAC)
         ├── HTTP: 鉴权后透传 X-User-Id/X-Department-Id/X-Is-Admin 到头
         └── WebSocket: 代理到协作服务
         ↓
    下游服务 (仅信任网关头)
```

## 变更清单

### 一、协作服务删除项

| 文件 | 原因 |
|---|---|
| `util/JwtUtil.java` | JWT 签发/解析 |
| `util/JwtClaimsSupport.java` | JWT claims 解析工具 |
| `web/JwtAuthFilter.java` | JWT 认证过滤器 |
| `web/MutableRequestWrapper.java` | 仅 JwtAuthFilter 使用 |
| `config/FilterConfig.java` | 注册 JwtAuthFilter |
| `web/AuthController.java` | 独立登录/注册/退出 |
| `service/UserLoginService.java` | 登录服务接口 |
| `service/impl/UserLoginServiceImpl.java` | 登录服务实现 |
| `entity/SysUser.java` | 自有用户实体 |
| `entity/SysDept.java` | 自有部门实体（如存在） |
| `mapper/SysUserMapper.java` | 自有用户 Mapper |
| `mapper/SysDeptMapper.java` | 自有部门 Mapper（如存在） |
| `dto/UserLoginReqDTO.java` | 登录请求 DTO |
| `dto/UserLoginRespDTO.java` | 登录响应 DTO |
| `dto/UserRegisterReqDTO.java` | 注册请求 DTO |
| `dto/UserRegisterRespDTO.java` | 注册响应 DTO |
| `dto/UserDeletionReqDTO.java` | 删除用户 DTO |
| `application.yml` 中 `auth.jwt.*` | JWT 配置 |

### 二、网关新增用户查询接口

在 `SystemAdminController` 新增接口供协作服务查用户（HTTP 调用走内网不经过网关鉴权，因此不受 admin 角色限制）：

```
GET /api/system/users/batch?ids=1,2,3   → 返回 [{ userId, username, realName, deptId, deptName }]
GET /api/system/users/search?keyword=x  → 通讯录搜索（替代协作 ContactController）
```

在 RBAC 规则中为 batch 和 search 排除 admin 角色校验，仅要求登录即可。

### 三、协作服务新增 GatewayUserClient

新增 `integration/GatewayUserClient.java`，封装 HTTP 调网关批量查用户。注入替代原来 `SysUserMapper` 的查询。

改造的 9 个 Controller/Handler：

```
ChatController        — 会话列表展示用户名
ChatWebSocketHandler  — 发送消息时查发送人姓名
TaskController        — 任务分配人姓名
AnnouncementController — 公告发布人姓名
DocController         — 文档作者姓名
DocCommentController  — 评论人姓名
ApprovalController    — 审批申请人姓名
ContactController     — 通讯录查用户/部门
DocShareController    — 分享人姓名
```

### 四、WebSocket 走网关

网关 `application.yml` 新增路由：

```yaml
- id: collaboration-ws
  uri: lb://enterprise-collaboration-service
  predicates:
    - Path=/ws/**
```

Sa-Token 配置改造：WebSocket 连接时 token 通过 URL query param 传递，需配置 Sa-Token 从 URL 读取。

协作服务 WebSocket Handler 改造：
- `ChatWebSocketHandler`、`DocWebSocketHandler` 去掉 `JwtUtil` 依赖
- 改为从网关代理过来的请求中读取身份（从 query param 的 token Sa-Token 会自动校验，校验通过后网关在 header 或 session attributes 中注入身份信息）

网关白名单放行 `/ws/**` 路径本身，但 WebSocket 升级握手时 Sa-Token 从 query param 取 token 做登录校验，校验不通过则拒绝连接。

**实现要点：**
- Sa-Token 默认从 Header/Cookie 读取 token，需配置自定义 `SaTokenTokenResolver` 支持 query param 读取
- 鉴权通过后，`IdentityPropagationGlobalFilter` 将用户 ID 注入 header 透传到协作服务
- 协作服务 WebSocket Handler 从 `X-User-Id` header 读取当前用户，不再解析 JWT

### 五、网关细粒度 RBAC

`SaTokenConfig.java` 改造 `SaReactorFilter.setAuth()`，在 `StpUtil.checkLogin()` 之后按路径匹配权限码：

```
路径                                          权限码
──────────────────────────────────────────────────────────
GET  /api/kb/documents/**                     kb:document:read
POST/PUT/DELETE /api/kb/documents/**           kb:document:write
GET  /api/kb/bases/**                         kb:bases:read
POST/PUT/DELETE /api/kb/bases/**               kb:bases:write
POST /api/kb/agent/chat                       kb:agent:chat
GET  /api/kb/pipelines/**                     kb:pipeline:read
POST/PUT/DELETE /api/kb/pipelines/**           kb:pipeline:write

GET  /api/meetings                            collab:meeting:read
POST/PUT/DELETE /api/meetings/**               collab:meeting:write
POST/PUT/DELETE /api/tasks/**                  collab:task:write
POST/PUT/DELETE /api/todos/**                  collab:todo:write
POST/PUT/DELETE /api/docs/**                   collab:doc:write
GET  /api/chat/**                             collab:chat:use
POST/PUT /api/approvals/**                    collab:approval:write
POST /api/announcements/**                    collab:announcement:write

/api/system/users/batch                   仅需登录（非 admin）
/api/system/users/search                  仅需登录（非 admin）
/api/system/**                             admin 角色
```

Sa-Token 的 `SaTokenStpInterfaceImpl` 已自动从 DB 加载用户权限码列表，`StpUtil.checkPermission()` 直接可用。权限码本身通过 `SystemAdminController` 管理，存在 `sys_permission` 表。

### 六、Sa-Token 白名单调整

```yaml
app:
  security:
    whitelist:
      paths:
        - /api/system/health
        - /api/auth/login
        - /actuator/health
        - /actuator/info
        - /ws/**          # 新增 WebSocket
```

### 七、前端改造

`Documents.vue`、`Chats.vue` 中 WebSocket 连接地址从 `:8090` 改为网关地址，token 传参保持不变（已使用 Sa-Token）。

## 不在范围内的

- 协作服务 `ContactController` 的通讯录功能，改为调网关接口后保留
- knowledge-ai-service 不做变更
- workbench-service 不做变更
- 不做用户数据迁移（协作服务的 `sys_user` 表直接废弃）
