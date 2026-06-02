## Why

当前四个微服务的职责边界混乱：Gateway 承担了 RBAC 后台管理的业务逻辑，Collaboration 重复实现了一套认证体系，Workbench 只有 124 行代码却独占一个服务进程。服务拆分缺乏领域依据，导致代码重复、职责不清、运维负担不必要地增加。

## What Changes

- **新增平台管理服务（platform-admin-service）**：从 Gateway 拆出用户、角色、权限、部门、操作日志等 RBAC 管理能力，形成独立的"用户中心/管理后台"服务
- **Gateway 纯净化**：移除 `SystemAdminController` 及关联的业务层代码，回归纯粹的路由、认证、限流职责；同时吸收 Workbench 的 BFF 聚合端点（`/api/workbench/overview`、`/api/workbench/stats`）
- **Collaboration 去重**：删除重复的认证体系（`AuthController`、`JwtUtil`、`JwtAuthFilter`、`UserLoginService`），统一由 Gateway 签发和验证 JWT，Collaboration 只关注协作业务
- **删除 Workbench 服务**：BFF 聚合逻辑迁移到 Gateway，`enterprise-workbench-service` 模块移除
- **数据库整理**：Collaboration 中的 `sys_user` 表废弃，用户数据统一由平台管理服务维护

## Capabilities

### New Capabilities
- `platform-admin-service`: 平台管理服务 — 用户 CRUD、角色管理、权限管理、部门管理、操作日志查询，提供内部 API 供其他服务查询用户信息
- `gateway-bff`: Gateway BFF 聚合 — 工作台仪表盘数据聚合（overview、stats），从 collaboration 和 knowledge-ai 拉取数据拼装响应

### Modified Capabilities
- `gateway-auth`: 移除 RBAC 后台管理端点（`/api/system/**`），Gateway 仅保留认证（login/logout）、鉴权（JWT 验证）、限流、路由
- `collaboration-auth`: 删除协作服务中的独立认证体系（AuthController、JwtUtil、JwtAuthFilter），改为依赖 Gateway 传递的身份头

## Impact

- **Gateway**：删除 `SystemAdminController`、5 个 RBAC Domain 实体、5 个 Repository、`OpLogService`、`RbacUtil`、`PasswordConfig`；新增 BFF 聚合 Controller
- **Collaboration**：删除 `AuthController`、`JwtUtil`、`JwtAuthFilter`、`MutableRequestWrapper`、`FilterConfig`、`UserLoginService`、`UserLoginServiceImpl`、所有 `dto/User*` 类、`SysUser` 实体及其 Mapper
- **Workbench**：整个模块删除
- **新服务 platform-admin**：独立 Spring Boot 服务，使用 MyBatis-Plus（统一技术栈），从 Gateway 迁移 JPA 实体为 MyBatis-Plus 实体
- **数据库**：新建 `enterprise_platform` 库；Collaboration 的 `sys_user` 表废弃；Gateway 的 RBAC 表迁移后废弃
- **配置**：Nacos 配置中心需注册新服务；Gateway 路由表需更新（添加 platform-admin 路由，移除 workbench 路由）
