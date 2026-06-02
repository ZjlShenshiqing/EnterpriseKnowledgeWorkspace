## ADDED Requirements

### Requirement: User CRUD

平台管理服务 SHALL 提供用户的创建、查询、更新、删除能力。

- 创建用户时 MUST 使用 BCrypt 对密码进行哈希
- 查询支持分页和关键词搜索（username、realName）
- 更新支持修改 realName、deptId、enabled、roleCodes
- 删除为逻辑删除，设置 `deleted=1`
- 所有操作 MUST 记录到操作日志

#### Scenario: Admin creates a user
- **WHEN** 管理员 POST `/api/system/users` 携带 username、password、realName、deptId、roleCodes
- **THEN** 系统创建用户，密码 BCrypt 哈希，返回用户信息，并写入操作日志

#### Scenario: Admin lists users with search
- **WHEN** 管理员 GET `/api/system/users?keyword=张三&page=1&size=20`
- **THEN** 系统返回分页用户列表，按 username 和 realName 模糊匹配

#### Scenario: Admin deletes a user
- **WHEN** 管理员 DELETE `/api/system/users/{id}`
- **THEN** 系统将用户 deleted 标记为 1，写入操作日志

### Requirement: Role CRUD

平台管理服务 SHALL 提供角色的创建、查询、更新、删除能力。

- 角色关联权限（多对多）
- 查询角色时 MUST 附带关联的用户数量
- 删除时 MUST 检查是否有用户持有该角色

#### Scenario: Admin creates a role with permissions
- **WHEN** 管理员 POST `/api/system/roles` 携带 code、name、permissionCodes
- **THEN** 系统创建角色并关联指定权限，写入操作日志

#### Scenario: List all roles with user count
- **WHEN** 管理员 GET `/api/system/roles`
- **THEN** 系统返回所有角色列表，每个角色附带 userCount

### Requirement: Permission and Department management

平台管理服务 SHALL 提供权限和部门的查询与创建能力。

- 权限 code MUST 唯一
- 部门 name MUST 唯一
- 部门支持 parentId 树形结构

#### Scenario: Create a permission
- **WHEN** 管理员 POST `/api/system/permissions` 携带 code、name
- **THEN** 系统创建权限，code 重复时返回 PARAM_INVALID 错误

#### Scenario: Create a department with parent
- **WHEN** 管理员 POST `/api/system/depts` 携带 name、parentId
- **THEN** 系统创建部门，name 重复时返回错误

### Requirement: Operation log query

平台管理服务 SHALL 提供操作日志的分页查询。

- 支持按 keyword 和 action 类型筛选
- 日志由服务内部异步写入，不暴露写入 API

#### Scenario: Admin queries operation logs
- **WHEN** 管理员 GET `/api/system/logs?keyword=CREATE_USER&action=&page=1&size=20`
- **THEN** 系统返回分页操作日志列表

### Requirement: Internal user query API

平台管理服务 SHALL 暴露内部 API 供其他服务查询用户信息。

- 支持批量查询：GET `/api/system/users/batch?ids=1,2,3`
- 支持关键词搜索：GET `/api/system/users/search?keyword=张&limit=50`
- 内部 API 不需要 admin 角色

#### Scenario: Collaboration service queries users by IDs
- **WHEN** 协作服务 GET `/api/system/users/batch?ids=100,200,300`
- **THEN** 系统返回 Map<Long, UserInfoDTO>，包含 id、username、realName

#### Scenario: Contact picker searches users
- **WHEN** GET `/api/system/users/search?keyword=李&limit=20`
- **THEN** 系统返回匹配的用户列表，最多 20 条

### Requirement: Service identity via headers

平台管理服务 SHALL 通过 `X-User-Id`、`X-Department-Id`、`X-Project-Id`、`X-Is-Admin` 请求头识别当前用户身份，与 Knowledge-AI 和 Collaboration 使用相同的 `UserContextInterceptor` 机制。

#### Scenario: Request with admin headers
- **WHEN** 请求携带 `X-User-Id: 1` 和 `X-Is-Admin: true`
- **THEN** `UserContextHolder.get()` 返回 isAdmin=true 的 UserContext
