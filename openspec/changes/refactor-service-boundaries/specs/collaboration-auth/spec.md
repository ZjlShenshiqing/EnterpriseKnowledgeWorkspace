## ADDED Requirements

### Requirement: Collaboration authentication removed

协作服务 SHALL NOT 实现独立的用户认证体系。

以下类 MUST 被移除：
- `AuthController` — 登录/注册/登出端点
- `JwtUtil` — JWT 签发和验证
- `JwtAuthFilter` — 请求级 JWT 过滤器
- `MutableRequestWrapper` — 请求包装器（仅被 JwtAuthFilter 使用）
- `FilterConfig` — 过滤器注册配置
- `UserLoginService` / `UserLoginServiceImpl` — 用户登录业务逻辑
- 所有 `dto/User*` 类（UserLoginReqDTO、UserLoginRespDTO、UserRegisterReqDTO、UserRegisterRespDTO、UserDeletionReqDTO）

#### Scenario: Collaboration service receives request with identity headers
- **WHEN** Gateway 代理请求到协作服务，携带 `X-User-Id`、`X-Is-Admin` 头
- **THEN** 协作服务的 `UserContextInterceptor` 解析头信息并设置 `UserContext`，Controller 通过 `UserContextHolder.get()` 获取当前用户

### Requirement: Collaboration SysUser entity removed

协作服务 SHALL 删除本地的 `SysUser` 实体、`SysUserMapper` 和 `sys_user` 表。

协作服务中其他实体对 userId 的引用（如 `ImMessage.senderId`、`SysTask.creatorId`、`SysTask.assigneeId`）SHALL 保持为 Long 类型的外键，不建立强类型关联。

#### Scenario: Resolve user name for display
- **WHEN** 协作服务需要显示用户名（如 "张三分"）
- **THEN** 服务通过 Feign 客户端调用平台管理服务的 `/api/system/users/batch` 批量获取用户信息

### Requirement: ContactController adapted to platform admin service

协作服务的 `ContactController`（通讯录端点）SHALL 通过 Feign 调用平台管理服务的用户搜索 API 获取用户列表，而非直接查询本地的 `sys_user` 表。

#### Scenario: Search contacts by keyword
- **WHEN** 用户 GET `/api/contacts/search?keyword=李`
- **THEN** ContactController 通过 Feign 调用 `platformAdminClient.searchUsers("李", 50)`，返回用户列表

### Requirement: Backward compatible API paths

协作服务的业务 API 路径 SHALL NOT 变更。仅移除认证相关端点（`/api/auth/**`）。

#### Scenario: Business endpoints unchanged
- **WHEN** 请求 `/api/tasks`、`/api/meetings`、`/api/approvals`、`/api/chat`、`/api/docs`、`/api/todos`、`/api/announcements`
- **THEN** 系统行为与改造前一致
