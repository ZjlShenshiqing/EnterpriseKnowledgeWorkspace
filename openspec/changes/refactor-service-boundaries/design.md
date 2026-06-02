## Context

当前项目 4 个微服务的职责边界混乱：

```
现状问题：
  Gateway      ─ 路由 + 认证 + RBAC后台管理（业务逻辑越界）
  Collaboration ─ 独立认证体系（JWT/登录/注册，与Gateway重复）
  Workbench    ─ 124行BFF聚合，不足以为独立服务
  Knowledge-AI ─ 最成熟，保持不动
```

Collaboration 拥有自己的 `sys_user` 表和完整认证流程（`JwtUtil`、`AuthController`、`JwtAuthFilter`），与 Gateway 的认证完全平行但互不通信。Gateway 的 `SystemAdminController` 承载了用户/角色/权限/部门/操作日志的完整 CRUD，这些是业务逻辑而非网关职责。

## Goals / Non-Goals

**Goals:**
- 将 RBAC 后台管理从 Gateway 拆出为独立的平台管理服务
- 消除 Collaboration 中重复的认证体系，统一使用 Gateway 签发的 JWT
- 将 Workbench 的 BFF 聚合端点迁移到 Gateway，删除空壳服务
- 统一新服务的持久层为 MyBatis-Plus（与 knowledge-ai 和 collaboration 保持一致）
- 平台管理服务提供内部 API 供其他服务查询用户信息

**Non-Goals:**
- 不拆分 Collaboration 的 7 个业务域（IM/会议/任务/审批/文档/公告/待办）——本次只清理认证
- 不修改 Knowledge-AI 的任何代码
- 不引入新的认证协议（OAuth2/OIDC）——继续使用现有 JWT 方案
- 不修改前端代码（API 路径和行为保持不变，前端无需改动）

## Decisions

### 1. 平台管理服务技术栈：MyBatis-Plus

**选择**：使用 MyBatis-Plus 而非 Gateway 当前的 JPA/Hibernate。

**理由**：Knowledge-AI 和 Collaboration 已使用 MyBatis-Plus，统一技术栈降低维护成本。Gateway 使用 JPA 是因为 Spring Data JPA 与 WebFlux 配合更方便，但新服务是独立 Servlet 容器，不受此限制。

**替代方案**：继续使用 JPA —— 但需要保留两套 ORM 体系，增加认知负担。

### 2. 数据库：新建 `enterprise_platform` 库

**选择**：平台管理服务使用独立数据库 `enterprise_platform`。

**理由**：微服务间不应共享数据库。当前 Gateway 的 `enterprise_gateway` 库混放了网关基础设施表（token_blacklist）和业务表（sys_user/role/permission/dept），拆出后各自独立。

**迁移策略**：新服务启动时从 Gateway 数据库迁移业务表数据，Gateway 保留 token_blacklist 表。

### 3. Gateway 吸收 BFF 聚合

**选择**：Workbench 的 `/api/workbench/overview` 和 `/api/workbench/stats` 端点迁移到 Gateway 内实现。

**理由**：
- Gateway 已是所有请求的入口，BFF 聚合需要调用多个下游服务，放在 Gateway 可减少一次网络跳转
- BFF 端点仅 124 行，不值得独立服务
- 前端无需改动：`/api/workbench/**` 路由从 Gateway 自身处理（不转发）

**替代方案**：前端直接调多个接口自行聚合 —— 增加前端复杂度，且多次请求增加网络延迟。

### 4. 用户身份传播保持现有头模式

**选择**：继续使用 `X-User-Id`、`X-Department-Id`、`X-Is-Admin` 请求头在下游服务间传播用户身份。

**理由**：Knowledge-AI 和 Collaboration 已依赖 `UserContextInterceptor` 解析这些头，不做变更可以最小化影响面。平台管理服务的内部 API（用户搜索、批量查询）也使用相同机制。

## Risks / Trade-offs

- **数据迁移风险**：Gateway JPA 实体 → 平台服务 MyBatis-Plus 实体，表结构可能不完全一致，需要验证迁移 SQL
  → 迁移前先对比两张表结构，编写迁移脚本并在开发环境验证

- **Collaboration 的 sys_user 依赖**：协作服务的多个实体（ImMessage.senderId、SysTask.creatorId 等）通过 Long 引用 userId。删除本地的 sys_user 表后，用户姓名解析需要通过平台管理服务的内部 API
  → 新增 Feign 客户端调用平台服务的 `/api/system/users/batch` 端点

- **Gateway 代码大量删除**：SystemAdminController（416 行）、5 个 Domain 实体、5 个 Repository、多个 Service 类需删除
  → 分步进行，先建新服务验证可用，再删除旧代码

- **新服务增加运维负担**：多一个 JVM 进程、多一个数据库连接池、多一个 Nacos 注册实例
  → 对当前阶段可接受，换来职责清晰

## Open Questions

- Collaboration 的 `SysUser` 实体删除后，`ContactController`（通讯录）如何获取用户列表？方案：通过 Feign 调用平台管理服务的用户搜索 API
- 平台管理服务的 API 是否需要 Admin 角色校验？方案：是，通过 Gateway 传递的 `X-Is-Admin` 头 + 服务自身拦截器
