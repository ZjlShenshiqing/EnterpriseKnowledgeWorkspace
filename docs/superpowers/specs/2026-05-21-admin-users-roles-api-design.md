# Admin Dashboard 子项目 1：用户与角色 API 对接设计

> 日期：2026-05-21
> 状态：设计中
> 归属：Admin Dashboard 后端 API 对接 - 子项目 1

## 背景

管理后台 14 个页面中只有 3 个（Bases、Documents、KnowledgeHub）接了真实 API，其余 11 个是 mock 数据。
本子项目将 Users.vue 和 Roles.vue 接入 Gateway 已有后端，目标为完整 CRUD。

所选方案：**方案 B — 抽取 Service 层 + Controller 纯转发**。

## 第一节：数据库与实体变更

### 数据库

```sql
ALTER TABLE sys_user ADD COLUMN real_name VARCHAR(64) DEFAULT NULL;
```

此变更兼容已有数据（DEFAULT NULL），不影响现有查询。

### SysUser 实体

在 `com.zjl.domain.SysUser` 中新增字段：

```java
/** 真实姓名 */
@Column(length = 64)
private String realName;
```

SysRole、SysPermission、SysDept 不做修改。

## 第二节：Service 层拆分

### 新建文件

```
enterprise-gateway-service/src/main/java/com/zjl/service/
  UserService.java    ← 新建
  RoleService.java    ← 新建
```

Controller 当前直接调用 Repository，逻辑内联在 Controller 方法中。抽 Service 后将业务逻辑迁入 Service，
Controller 变为纯参数校验与路由转发。

### UserService 职责

| 方法 | 说明 |
|------|------|
| `PageResult<SysUser> listUsers(String keyword, int page, int size)` | 分页查询，keyword 搜索 username 和 realName |
| `SysUser getUser(Long id)` | 按 ID 查询，不存在抛 BizException(40400) |
| `SysUser createUser(CreateUserRequest req)` | 创建用户，含用户名唯一性检查、密码 BCrypt 加密、角色绑定 |
| `SysUser updateUser(Long id, UpdateUserRequest req)` | 更新 realName、deptId、enabled、roleCodes |
| `void deleteUser(Long id)` | 删除用户 |
| `UserStats getUserStats()` | 返回总数、启用数、禁用数、管理员数 |

### RoleService 职责

| 方法 | 说明 |
|------|------|
| `List<RoleDTO> listRoles()` | 角色列表，每个角色附带 userCount |
| `SysRole getRole(Long id)` | 按 ID 查询 |
| `SysRole createRole(CreateRoleRequest req)` | 创建角色，含 code 唯一性检查、权限绑定 |
| `SysRole updateRole(Long id, UpdateRoleRequest req)` | 更新 name 和 permissionCodes |
| `void deleteRole(Long id)` | 删除角色，检查是否有用户绑定（有则抛异常） |

### RoleDTO

因角色需附带 userCount（不在 SysRole 实体中），新增内部 DTO：

```java
public record RoleDTO(Long id, String code, String name,
        Set<SysPermission> permissions, long userCount,
        Instant createdAt, Instant updatedAt) {
    public static RoleDTO from(SysRole role, long userCount) {
        return new RoleDTO(role.getId(), role.getCode(), role.getName(),
                role.getPermissions(), userCount,
                role.getCreatedAt(), role.getUpdatedAt());
    }
}
```

## 第三节：API 变更 — SystemAdminController

所有端点均在 `/api/system/*` 路径下，需要 `@PreAuthorize("hasRole('ADMIN')")`。
Controller 从直接注入 Repository 改为注入 Service。

### 用户端点

| 方法 | 路径 | 说明 | 变更类型 |
|------|------|------|---------|
| `GET` | `/users?keyword=&page=1&size=20` | 分页列表，keyword 搜 username/realName | 修改 |
| `GET` | `/users/{id}` | 单个用户详情 | 新增 |
| `GET` | `/users/stats` | 汇总统计（总数/启用/禁用/管理员数） | 新增 |
| `POST` | `/users` | 创建用户 | 修改（加 realName） |
| `PUT` | `/users/{id}` | 编辑用户（realName、deptId、enabled、roleCodes） | 新增 |
| `PUT` | `/users/{id}/roles` | 只改角色 | 已有，保持 |
| `DELETE` | `/users/{id}` | 删除用户 | 新增 |

**CreateUserRequest 变更**：

```java
public record CreateUserRequest(
    @NotBlank String username,
    @NotBlank String password,
    String realName,          // 新增
    Long deptId,              // 新增
    Set<String> roleCodes
) {}
```

**新增 UpdateUserRequest**：

```java
public record UpdateUserRequest(
    String realName,
    Long deptId,
    Boolean enabled,
    Set<String> roleCodes
) {}
```

**UserStats 返回**：

```java
public record UserStats(long total, long enabled, long admin, long disabled) {}
```

### 角色端点

| 方法 | 路径 | 说明 | 变更类型 |
|------|------|------|---------|
| `GET` | `/roles` | 角色列表，附带 userCount | 修改（返回类型改为 RoleDTO） |
| `GET` | `/roles/{id}` | 单个角色详情 | 新增 |
| `POST` | `/roles` | 创建角色 | 已有，保持 |
| `PUT` | `/roles/{id}` | 编辑角色（name、permissionCodes） | 新增 |
| `DELETE` | `/roles/{id}` | 删除角色 | 新增 |

**新增 UpdateRoleRequest**：

```java
public record UpdateRoleRequest(String name, Set<String> permissionCodes) {}
```

### 分页返回格式

使用项目已有的 `PageResult`：

```json
{
  "code": 0,
  "message": null,
  "data": {
    "current": 1,
    "size": 20,
    "total": 35,
    "records": [...]
  },
  "traceId": "..."
}
```

### 权限与部门

已有 `GET/POST /api/system/permissions` 和 `GET/POST /api/system/depts`，不做变更。
前端在编辑用户（选部门）和编辑角色（选权限）时从这些端点取选项列表。

## 第四节：前端变更

### API 模块扩展（`src/api/index.js`）

新增以下函数，均使用已有的 `systemApi` 实例（base URL `/api/system`）：

```javascript
// 用户
getUsers(params)              → GET /api/system/users?keyword=&page=&size=
getUser(id)                   → GET /api/system/users/{id}
createUser(body)              → POST /api/system/users
updateUser(id, body)          → PUT /api/system/users/{id}
deleteUser(id)                → DELETE /api/system/users/{id}
getUserStats()                → GET /api/system/users/stats

// 角色
getRoles()                    → GET /api/system/roles
getRole(id)                   → GET /api/system/roles/{id}
createRole(body)              → POST /api/system/roles
updateRole(id, body)          → PUT /api/system/roles/{id}
deleteRole(id)                → DELETE /api/system/roles/{id}
```

### Users.vue 改造

| 部分 | 当前 | 改造后 |
|------|------|--------|
| 顶部统计卡片 | `users.length` 等本地计算 | 调用 `getUserStats()`，reactive 绑定 |
| 表格数据 | `ref([...])` 硬编码 | 调用 `getUsers({ keyword, page, size })` |
| 搜索 | 无 | 新增 `el-input` 搜索框，防抖 300ms |
| 分页 | 无 | 新增 `el-pagination`，`currentPage`/`pageSize` 状态 |
| 新增用户 | 按钮无逻辑 | `el-dialog` 弹窗表单：username、realName、password、dept（下拉）、roles（多选） |
| 行操作列 | 无 | 每行加：编辑按钮、启停开关、删除按钮 |
| 编辑弹窗 | 无 | `el-dialog` 弹窗表单：realName、dept（下拉）、enabled（开关）、roles（多选） |
| 删除 | 无 | `el-message-box` 确认弹窗 |
| 筛选标签 | 静态 HTML | 保留 UI，事件暂不接后端（后续迭代） |

部门下拉框数据从已有 `GET /api/system/depts` 获取。
角色多选框数据从已有 `GET /api/system/roles` 获取。

### Roles.vue 改造

| 部分 | 当前 | 改造后 |
|------|------|--------|
| 表格数据 | `ref([...])` 硬编码 | 调用 `getRoles()` |
| 新增角色 | 按钮无逻辑 | `el-dialog` 弹窗表单：code、name、permission（多选，从 `GET /api/system/permissions` 获取） |
| 编辑 | 按钮无逻辑 | `el-dialog` 弹窗表单：name、permission（多选） |
| 删除 | 按钮无逻辑 | `el-message-box` 确认弹窗 |
| 权限数 | 本地数字 `15` | `role.permissions.length`（后端返回） |
| 用户数 | 本地数字 `1` | `role.userCount`（后端 RoleDTO 附带） |

### 不做的事

- 不拆组件，不改文件结构
- 不做批量操作、导入导出
- 不引入状态管理库
- 筛选标签事件暂时不接（后续迭代）

## 第五节：错误处理

复用项目已有的 `GlobalExceptionHandler` + `BizException` + `ErrorCode` 体系。

| 场景 | ErrorCode | 消息 |
|------|-----------|------|
| 用户名已存在 | 40000 | 用户名已存在 |
| 角色 code 已存在 | 40000 | 角色 code 已存在 |
| 用户不存在 | 40400 | 用户不存在 |
| 角色不存在 | 40400 | 角色不存在 |
| 删除有用户的角色 | 40000 | 角色下存在用户，无法删除 |
| 参数校验失败 | PARAM_INVALID | 由 Jakarta Validation 自动生成 |
| 非管理员访问 | — | Spring Security 自动返回 403 |

## 第六节：文件变更汇总

### 后端（Gateway）

| 文件 | 变更 |
|------|------|
| `domain/SysUser.java` | 加 `realName` 字段 |
| `service/UserService.java` | **新建** |
| `service/RoleService.java` | **新建** |
| `service/RoleDTO.java` | **新建** |
| `web/SystemAdminController.java` | 注入 Service 替代 Repository，补全 CRUD |

### 数据库

| 变更 | 表 |
|------|-----|
| `ALTER TABLE ADD COLUMN real_name` | `sys_user` |

### 前端

| 文件 | 变更 |
|------|------|
| `src/api/index.js` | 加 11 个 API 函数 |
| `src/pages/admin/Users.vue` | 替换 mock，接通 CRUD + 分页 + 搜索 |
| `src/pages/admin/Roles.vue` | 替换 mock，接通 CRUD |

## 附录：创建用户时的角色引用

用户创建/编辑时指定角色使用 `roleCodes`（如 `["admin", "manager"]`），而非角色 ID。
这与现有 `CreateUserRequest.roleCodes` 和 `CreateRoleRequest.permissionCodes` 的模式保持一致。
