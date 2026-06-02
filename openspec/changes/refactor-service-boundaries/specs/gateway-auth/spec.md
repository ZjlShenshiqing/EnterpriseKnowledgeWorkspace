## ADDED Requirements

### Requirement: Gateway pure routing and auth

Gateway SHALL 仅负责以下基础设施职责，不再包含任何业务 CRUD 逻辑：

- 将匹配路由的请求代理转发到下游服务（使用 Nacos `lb://` 服务发现）
- JWT 令牌的签发（login）和吊销（logout）
- 对每个请求验证 JWT 并注入 `X-User-Id`、`X-Department-Id`、`X-Is-Admin` 请求头
- IP 黑白名单和按 IP 限流
- TraceId 传播

#### Scenario: Authenticated request is routed to downstream
- **WHEN** 请求携带有效 JWT 令牌访问 `/api/kb/documents`
- **THEN** Gateway 验证 JWT，注入身份头，将请求代理到 `lb://enterprise-knowledge-ai-service`

#### Scenario: Unauthenticated request is rejected
- **WHEN** 请求未携带 JWT 令牌访问非白名单路径
- **THEN** Gateway 返回 401 Unauthorized

### Requirement: RBAC admin endpoints removed from Gateway

Gateway SHALL NOT 包含以下端点（迁移至 platform-admin-service）：

- `/api/system/users/**` — 用户管理
- `/api/system/roles/**` — 角色管理
- `/api/system/permissions/**` — 权限管理
- `/api/system/depts/**` — 部门管理
- `/api/system/logs/**` — 操作日志

这些路径 SHALL 在 Gateway 路由表中指向 `lb://platform-admin-service`。

#### Scenario: Admin routes forwarded to platform service
- **WHEN** 管理员请求 `GET /api/system/users?page=1&size=20`
- **THEN** Gateway 验证 JWT 和 admin 角色后，将请求代理到 `lb://platform-admin-service`

### Requirement: Workbench module removed from Gateway routing

Gateway SHALL NOT 将 `/api/workbench/**` 代理到外部服务。此路径由 Gateway 自身的 BFF 聚合 Controller 处理。

#### Scenario: Workbench overview handled by Gateway itself
- **WHEN** 用户 GET `/api/workbench/overview`
- **THEN** 请求由 Gateway 内部的 WorkbenchController 处理，不转发到其他服务
