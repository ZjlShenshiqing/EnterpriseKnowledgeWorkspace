## 1. 平台管理服务 — 模块搭建

- [x] 1.1 创建 `enterprise-platform-admin-service` Maven 模块，配置 parent POM 和依赖（Spring Boot Web、MyBatis-Plus、MySQL、Nacos Discovery、Nacos Config、frameworks-web-spring-boot-starter）
- [x] 1.2 创建 `PlatformAdminApplication.java` 主启动类，启用 `@EnableDiscoveryClient` 和 `@MapperScan`
- [x] 1.3 编写 `application.yml`（服务端口 8084、数据库连接 `enterprise_platform`、Nacos 配置）
- [x] 1.4 更新 parent POM 添加新模块引用

## 2. 平台管理服务 — 数据层

- [x] 2.1 创建 MyBatis-Plus 实体类：`SysUser`、`SysRole`、`SysPermission`、`SysDept`、`SysOpLog`、`SysUserRole`、`SysRolePermission`
- [x] 2.2 创建 MyBatis-Plus Mapper 接口：`SysUserMapper`、`SysRoleMapper`、`SysPermissionMapper`、`SysDeptMapper`、`SysOpLogMapper`
- [x] 2.3 编写数据库初始化 SQL（建表语句、索引），输出到 `src/main/resources/schema.sql`
- [x] 2.4 编写数据迁移 SQL：从 `enterprise_gateway` 库的 sys_user/role/permission/dept/op_log 表迁移数据到 `enterprise_platform` 库（输出为独立 SQL 文件）

## 3. 平台管理服务 — 业务层

- [x] 3.1 实现 `UserService`（用户分页查询、搜索、创建、更新、删除、批量查询）
- [x] 3.2 实现 `RoleService`（角色 CRUD、关联权限、查询附带用户数）
- [x] 3.3 实现 `OpLogService`（异步操作日志写入、分页查询）
- [x] 3.4 实现 `SystemAdminController`（`/api/system/users`、`/api/system/roles`、`/api/system/permissions`、`/api/system/depts`、`/api/system/logs`），适配 Servlet（原 Gateway 为 WebFlux Mono）
- [x] 3.5 实现 `UserContextInterceptor`，从 `X-User-Id` 等头解析当前用户

## 4. Gateway 改造

- [x] 4.1 删除 RBAC 管理代码：`SystemAdminController`、`UserService`/`UserServiceImpl`、`RoleService`/`RoleServiceImpl`、`OpLogService`/`OpLogServiceImpl`、`SysPermission`、`SysOpLog` 及其 Repository、`RoleDTO`、`UserInfoDTO`；保留 `SysUser`/`SysRole`/`SysDept` 及 PasswordConfig 用于登录鉴权
- [x] 4.2 新增 BFF 聚合 `WorkbenchController`，实现 `/api/workbench/overview` 和 `/api/workbench/stats`（使用 WebClient 调用下游服务）
- [x] 4.3 更新 Gateway 路由配置：`/api/system/**` → `lb://platform-admin-service`，移除 workbench 路由
- [x] 4.4 修复 `ContactDirectoryController` 和 `SysRole` 的编译错误（移除已删除类的引用）

## 5. Collaboration 改造

- [x] 5.1 认证代码已在前序工作中删除（AuthController、JwtUtil、JwtAuthFilter 等均已不存在）
- [x] 5.2 `SysUser` 实体和 `SysUserMapper` 已在前序工作中删除
- [x] 5.3 `GatewayUserClient` 已存在，通过 HTTP 调用网关的 `/api/system/users/batch` 和 `/api/system/users/search`
- [x] 5.4 `ContactController` 已通过 `GatewayUserClient` 获取用户列表
- [x] 5.5 其他 Controller 已使用 `GatewayUserClient` 获取用户信息

## 6. Workbench 服务删除

- [x] 6.1 从 parent POM 移除 `enterprise-workbench-service` 模块
- [x] 6.2 删除 `enterprise-workbench-service` 目录

## 7. Nacos 与配置中心

- [ ] 7.1 启动 platform-admin-service 时自动注册到 Nacos（`@EnableDiscoveryClient` + application.yml 配置已完成）
- [x] 7.2 Gateway 路由已更新：`/api/system/**` → `lb://platform-admin-service`，workbench 路由已移除
- [ ] 7.3 Collaboration 的 JWT 认证配置已在前序工作中移除

## 8. 数据库迁移（需要 MySQL 环境）

- [ ] 8.1 在开发环境执行 `schema.sql` 建表 + `migration.sql` 数据迁移
- [ ] 8.2 废弃 Collaboration 的 `sys_user` 表（已在前序工作中处理）
- [ ] 8.3 Gateway `enterprise_gateway` 库可保留 sys_user/sys_role/sys_dept 表供登录鉴权使用；sys_permission/sys_op_log 表可废弃

## 9. 集成验证（需要可运行的环境）

- [ ] 9.1 编译项目：`mvn clean compile -DskipTests`，修复可能的编译错误
- [ ] 9.2 启动所有服务，验证 Nacos 注册正常
- [ ] 9.3 验证 Gateway 路由：`/api/system/**` 正确转发到 platform-admin-service
- [ ] 9.4 验证 BFF 聚合：`/api/workbench/overview` 返回正确聚合数据
- [ ] 9.5 验证前端登录和鉴权流程不受影响
