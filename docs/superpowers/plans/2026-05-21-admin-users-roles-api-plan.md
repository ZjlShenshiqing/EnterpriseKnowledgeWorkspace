# Admin 用户与角色 API 对接实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将管理后台 Users.vue 和 Roles.vue 从 mock 数据切换到 Gateway 真实 API，含完整 CRUD、分页搜索和统计。

**Architecture:** Gateway 内部抽取 UserService/RoleService 层（从 Controller 迁出业务逻辑），Controller 转为纯转发。前端 api/index.js 加 system API 函数，Users.vue 和 Roles.vue 替换 mock。role 列表附带 userCount（通过 JPQL 子查询），user 分页支持 keyword 模糊搜索。

**Tech Stack:** Java 17, Spring Boot 3.4.4, WebFlux (Reactor), JPA/Hibernate, MySQL; Vue 3, Element Plus, Axios

**Spec:** `docs/superpowers/specs/2026-05-21-admin-users-roles-api-design.md`

---

### Task 1: 数据库迁移 — sys_user 加 real_name 列

**Files:**
- Create: `enterprise-gateway-service/src/main/resources/db/migration/001-add-real-name.sql`

> 注：项目已有 `spring.sql.init.mode=always` + `schema.sql` 模式。因是追加字段，用独立迁移文件，手动执行。

- [ ] **Step 1: 创建 SQL 迁移文件**

```sql
ALTER TABLE sys_user ADD COLUMN IF NOT EXISTS real_name VARCHAR(64) DEFAULT NULL;
```

- [ ] **Step 2: 执行迁移**

```bash
mysql -u root -p enterprise_gateway < enterprise-gateway-service/src/main/resources/db/migration/001-add-real-name.sql
```

若 `IF NOT EXISTS` 不可用（MySQL 5.x），改为裸 `ALTER TABLE`：
```sql
ALTER TABLE sys_user ADD COLUMN real_name VARCHAR(64) DEFAULT NULL;
```

验证：
```sql
DESCRIBE sys_user;
-- 应看到 real_name 列，varchar(64), YES, NULL
```

- [ ] **Step 3: 验证已有数据不受影响**

```sql
SELECT id, username, real_name FROM sys_user;
-- 已有行的 real_name 应为 NULL
```

- [ ] **Step 4: Commit**

```bash
git add enterprise-gateway-service/src/main/resources/db/migration/001-add-real-name.sql
git commit -m "feat: sys_user 表加 real_name 列"
```

---

### Task 2: SysUser 实体加 realName + UserRepository 加统计查询

**Files:**
- Modify: `enterprise-gateway-service/src/main/java/com/zjl/domain/SysUser.java` (加 realName 字段)
- Modify: `enterprise-gateway-service/src/main/java/com/zjl/repository/SysUserRepository.java` (加分页搜索 + 角色人数统计)

- [ ] **Step 1: SysUser 加 realName 字段**

在 `passwordHash` 字段之后、`dept` 字段之前插入：

```java
/**
 * 真实姓名
 */
@Column(length = 64)
private String realName;
```

- [ ] **Step 2: UserRepository 加分页搜索方法**

```java
package com.zjl.repository;

import com.zjl.domain.SysUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * 用户数据访问层
 */
public interface SysUserRepository extends JpaRepository<SysUser, Long> {

    /**
     * 按用户名查询用户
     *
     * @param username 用户名
     * @return 用户（可能为空）
     */
    Optional<SysUser> findByUsername(String username);

    /**
     * 分页模糊搜索用户（按 username 或 realName）
     *
     * @param keyword 搜索关键词
     * @param pageable 分页参数
     * @return 分页结果
     */
    @Query("SELECT u FROM SysUser u WHERE u.username LIKE %:keyword% OR u.realName LIKE %:keyword%")
    Page<SysUser> searchUsers(@Param("keyword") String keyword, Pageable pageable);

    /**
     * 统计拥有指定角色的用户数
     *
     * @param roleId 角色 ID
     * @return 用户数
     */
    @Query("SELECT COUNT(u) FROM SysUser u JOIN u.roles r WHERE r.id = :roleId")
    long countByRoleId(@Param("roleId") Long roleId);

    /**
     * 统计管理员数量（角色 code = 'admin'）
     *
     * @return 管理员数
     */
    @Query("SELECT COUNT(u) FROM SysUser u JOIN u.roles r WHERE r.code = 'admin'")
    long countAdmin();
}
```

- [ ] **Step 3: 验证编译**

```bash
mvn compile -pl enterprise-gateway-service -am
```

预期：BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add enterprise-gateway-service/src/main/java/com/zjl/domain/SysUser.java enterprise-gateway-service/src/main/java/com/zjl/repository/SysUserRepository.java
git commit -m "feat: SysUser 加 realName，UserRepository 加分页搜索和统计查询"
```

---

### Task 3: RoleDTO 记录类

**Files:**
- Create: `enterprise-gateway-service/src/main/java/com/zjl/service/RoleDTO.java`

- [ ] **Step 1: 创建 RoleDTO**

```java
package com.zjl.service;

import com.zjl.domain.SysPermission;
import com.zjl.domain.SysRole;

import java.time.Instant;
import java.util.Set;

/**
 * 角色数据传输对象，附带关联用户数统计
 *
 * @param id 角色 ID
 * @param code 角色编码
 * @param name 角色名称
 * @param permissions 权限集合
 * @param userCount 拥有该角色的用户数
 * @param createdAt 创建时间
 * @param updatedAt 更新时间
 */
public record RoleDTO(
        Long id,
        String code,
        String name,
        Set<SysPermission> permissions,
        long userCount,
        Instant createdAt,
        Instant updatedAt
) {
    /**
     * 从 SysRole 实体转换
     *
     * @param role 角色实体
     * @param userCount 关联用户数
     * @return RoleDTO
     */
    public static RoleDTO from(SysRole role, long userCount) {
        return new RoleDTO(
                role.getId(),
                role.getCode(),
                role.getName(),
                role.getPermissions(),
                userCount,
                role.getCreatedAt(),
                role.getUpdatedAt()
        );
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add enterprise-gateway-service/src/main/java/com/zjl/service/RoleDTO.java
git commit -m "feat: RoleDTO 记录类 — 角色列表附带 userCount"
```

---

### Task 4: UserService

**Files:**
- Create: `enterprise-gateway-service/src/main/java/com/zjl/service/UserService.java`

UserService 从 Controller 抽取用户业务逻辑，封装 JPA 阻塞调用。所有操作基于 `Schedulers.boundedElastic()` 避免阻塞 Netty IO 线程。

`UserService` 使用构造器注入，返回方法均为同步（`Mono.fromCallable` 由 Controller 处理调度）。

- [ ] **Step 1: 创建 UserService**

```java
package com.zjl.service;

import com.zjl.common.exception.BizException;
import com.zjl.common.response.PageResult;
import com.zjl.domain.SysDept;
import com.zjl.domain.SysRole;
import com.zjl.domain.SysUser;
import com.zjl.repository.SysDeptRepository;
import com.zjl.repository.SysRoleRepository;
import com.zjl.repository.SysUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 用户管理业务逻辑
 *
 * <p>封装 JPA 阻塞调用，供 Controller 通过 Mono.fromCallable 在弹性线程池中调用。</p>
 */
@Service
@Transactional
public class UserService {

    private final SysUserRepository userRepository;
    private final SysRoleRepository roleRepository;
    private final SysDeptRepository deptRepository;
    private final BCryptPasswordEncoder passwordEncoder;

    public UserService(
            SysUserRepository userRepository,
            SysRoleRepository roleRepository,
            SysDeptRepository deptRepository,
            BCryptPasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.deptRepository = deptRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * 分页用户列表
     *
     * @param keyword 搜索关键词（username 或 realName），null 或空则全量
     * @param page 页码，从 1 开始
     * @param size 每页条数
     * @return 分页结果
     */
    public PageResult<SysUser> listUsers(String keyword, int page, int size) {
        PageRequest pr = PageRequest.of(page - 1, size, Sort.by(Sort.Direction.DESC, "id"));
        Page<SysUser> result;
        if (keyword == null || keyword.isBlank()) {
            result = userRepository.findAll(pr);
        } else {
            result = userRepository.searchUsers(keyword, pr);
        }
        return PageResult.of(page, size, result.getTotalElements(), result.getContent());
    }

    /**
     * 按 ID 查询用户
     *
     * @param id 用户 ID
     * @return 用户实体
     * @throws BizException(40400) 用户不存在
     */
    public SysUser getUser(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "用户不存在"));
    }

    /**
     * 用户汇总统计
     *
     * @return UserStats(total, enabled, admin, disabled)
     */
    public UserStats getUserStats() {
        long total = userRepository.count();
        long admin = userRepository.countAdmin();
        long enabled = userRepository.findAll().stream().filter(SysUser::isEnabled).count();
        long disabled = total - enabled;
        return new UserStats(total, enabled, admin, disabled);
    }

    /**
     * 创建用户
     *
     * @param username 用户名
     * @param password 明文密码
     * @param realName 真实姓名（可选）
     * @param deptId 部门 ID（可选）
     * @param roleCodes 角色编码集合（可选）
     * @return 创建后的用户
     * @throws BizException(40000) 用户名已存在
     */
    public SysUser createUser(String username, String password, String realName,
                               Long deptId, Set<String> roleCodes) {
        if (userRepository.findByUsername(username).isPresent()) {
            throw new BizException(40000, "用户名已存在");
        }
        SysUser u = new SysUser();
        u.setUsername(username);
        u.setPasswordHash(passwordEncoder.encode(password));
        u.setRealName(realName);
        if (deptId != null) {
            SysDept dept = deptRepository.findById(deptId)
                    .orElseThrow(() -> new BizException(40400, "部门不存在"));
            u.setDept(dept);
        }
        if (roleCodes != null && !roleCodes.isEmpty()) {
            Set<SysRole> roles = resolveRoles(roleCodes);
            u.setRoles(roles);
        }
        return userRepository.save(u);
    }

    /**
     * 更新用户
     *
     * @param id 用户 ID
     * @param realName 真实姓名（null 则不更新）
     * @param deptId 部门 ID（null 则不更新）
     * @param enabled 是否启用（null 则不更新）
     * @param roleCodes 角色编码集合（null 则不更新）
     * @return 更新后的用户
     */
    public SysUser updateUser(Long id, String realName, Long deptId,
                               Boolean enabled, Set<String> roleCodes) {
        SysUser u = userRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "用户不存在"));
        if (realName != null) u.setRealName(realName);
        if (deptId != null) {
            SysDept dept = deptRepository.findById(deptId)
                    .orElseThrow(() -> new BizException(40400, "部门不存在"));
            u.setDept(dept);
        }
        if (enabled != null) u.setEnabled(enabled);
        if (roleCodes != null) {
            Set<SysRole> roles = resolveRoles(roleCodes);
            u.setRoles(roles);
        }
        return userRepository.save(u);
    }

    /**
     * 删除用户
     *
     * @param id 用户 ID
     */
    public void deleteUser(Long id) {
        if (!userRepository.existsById(id)) {
            throw new BizException(40400, "用户不存在");
        }
        userRepository.deleteById(id);
    }

    /**
     * 更新用户角色
     *
     * @param id 用户 ID
     * @param roleCodes 角色编码集合
     * @return 更新后的用户
     */
    public SysUser updateUserRoles(Long id, Set<String> roleCodes) {
        SysUser u = userRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "用户不存在"));
        Set<SysRole> roles = resolveRoles(roleCodes);
        u.setRoles(roles);
        return userRepository.save(u);
    }

    /**
     * 将角色编码集合转为 SysRole 实体集合
     */
    private Set<SysRole> resolveRoles(Set<String> roleCodes) {
        return roleCodes.stream()
                .map(code -> roleRepository.findByCode(code)
                        .orElseThrow(() -> new BizException(40000, "角色不存在: " + code)))
                .collect(Collectors.toSet());
    }

    /**
     * 用户汇总统计数据
     */
    public record UserStats(long total, long enabled, long admin, long disabled) {}
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -pl enterprise-gateway-service -am
```

预期：BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add enterprise-gateway-service/src/main/java/com/zjl/service/UserService.java
git commit -m "feat: UserService — 从 Controller 抽取用户业务逻辑"
```

---

### Task 5: RoleService

**Files:**
- Create: `enterprise-gateway-service/src/main/java/com/zjl/service/RoleService.java`

- [ ] **Step 1: 创建 RoleService**

```java
package com.zjl.service;

import com.zjl.common.exception.BizException;
import com.zjl.domain.SysPermission;
import com.zjl.domain.SysRole;
import com.zjl.repository.SysPermissionRepository;
import com.zjl.repository.SysRoleRepository;
import com.zjl.repository.SysUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 角色管理业务逻辑
 *
 * <p>封装 JPA 阻塞调用，供 Controller 通过 Mono.fromCallable 在弹性线程池中调用。</p>
 */
@Service
@Transactional
public class RoleService {

    private final SysRoleRepository roleRepository;
    private final SysPermissionRepository permissionRepository;
    private final SysUserRepository userRepository;

    public RoleService(
            SysRoleRepository roleRepository,
            SysPermissionRepository permissionRepository,
            SysUserRepository userRepository
    ) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.userRepository = userRepository;
    }

    /**
     * 角色列表（附带 userCount）
     *
     * @return 角色 DTO 列表
     */
    public List<RoleDTO> listRoles() {
        List<SysRole> roles = roleRepository.findAll();
        return roles.stream()
                .map(r -> RoleDTO.from(r, userRepository.countByRoleId(r.getId())))
                .collect(Collectors.toList());
    }

    /**
     * 按 ID 查询角色
     *
     * @param id 角色 ID
     * @return 角色实体
     * @throws BizException(40400) 角色不存在
     */
    public SysRole getRole(Long id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "角色不存在"));
    }

    /**
     * 创建角色
     *
     * @param code 角色编码
     * @param name 角色名称
     * @param permissionCodes 权限编码集合（可选）
     * @return 创建后的角色
     * @throws BizException(40000) 角色编码已存在
     */
    public SysRole createRole(String code, String name, Set<String> permissionCodes) {
        if (roleRepository.findByCode(code).isPresent()) {
            throw new BizException(40000, "角色 code 已存在");
        }
        SysRole r = new SysRole();
        r.setCode(code);
        r.setName(name);
        if (permissionCodes != null && !permissionCodes.isEmpty()) {
            Set<SysPermission> perms = resolvePermissions(permissionCodes);
            r.setPermissions(perms);
        }
        return roleRepository.save(r);
    }

    /**
     * 更新角色
     *
     * @param id 角色 ID
     * @param name 角色名称（null 则不更新）
     * @param permissionCodes 权限编码集合（null 则不更新）
     * @return 更新后的角色
     */
    public SysRole updateRole(Long id, String name, Set<String> permissionCodes) {
        SysRole r = roleRepository.findById(id)
                .orElseThrow(() -> new BizException(40400, "角色不存在"));
        if (name != null) r.setName(name);
        if (permissionCodes != null) {
            Set<SysPermission> perms = resolvePermissions(permissionCodes);
            r.setPermissions(perms);
        }
        return roleRepository.save(r);
    }

    /**
     * 删除角色
     *
     * @param id 角色 ID
     * @throws BizException(40000) 角色下存在用户
     */
    public void deleteRole(Long id) {
        if (!roleRepository.existsById(id)) {
            throw new BizException(40400, "角色不存在");
        }
        long userCount = userRepository.countByRoleId(id);
        if (userCount > 0) {
            throw new BizException(40000, "角色下存在用户，无法删除");
        }
        roleRepository.deleteById(id);
    }

    /**
     * 将权限编码集合转为 SysPermission 实体集合
     */
    private Set<SysPermission> resolvePermissions(Set<String> permissionCodes) {
        return permissionCodes.stream()
                .map(code -> permissionRepository.findByCode(code)
                        .orElseThrow(() -> new BizException(40000, "权限不存在: " + code)))
                .collect(Collectors.toSet());
    }
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -pl enterprise-gateway-service -am
```

预期：BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add enterprise-gateway-service/src/main/java/com/zjl/service/RoleService.java
git commit -m "feat: RoleService — 从 Controller 抽取角色业务逻辑，含 userCount"
```

---

### Task 6: SystemAdminController 重构

**Files:**
- Modify: `enterprise-gateway-service/src/main/java/com/zjl/web/SystemAdminController.java`

将 Controller 从直接注入 Repository 改为注入 Service，保留已有的 4 个端点逻辑不变，新增 7 个端点。

- [ ] **Step 1: 重写 SystemAdminController**

```java
package com.zjl.web;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.domain.SysDept;
import com.zjl.domain.SysPermission;
import com.zjl.domain.SysRole;
import com.zjl.domain.SysUser;
import com.zjl.repository.SysDeptRepository;
import com.zjl.repository.SysPermissionRepository;
import com.zjl.security.UserContext;
import com.zjl.service.OpLogService;
import com.zjl.dto.RoleDTO;
import com.zjl.service.RoleService;
import com.zjl.service.UserService;
import com.zjl.service.UserService.UserStats;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Set;

/**
 * 系统管理（RBAC）后台接口
 *
 * <p>约束：仅允许管理员访问（hasRole('ADMIN')）。</p>
 * <p>Controller 仅做路由转发和参数校验，业务逻辑在 Service 层。</p>
 */
@Validated
@RestController
@RequestMapping("/api/system")
@PreAuthorize("hasRole('ADMIN')")
public class SystemAdminController {

    private final UserService userService;
    private final RoleService roleService;
    private final SysPermissionRepository permissionRepository;
    private final SysDeptRepository deptRepository;
    private final OpLogService opLogService;

    public SystemAdminController(
            UserService userService,
            RoleService roleService,
            SysPermissionRepository permissionRepository,
            SysDeptRepository deptRepository,
            OpLogService opLogService
    ) {
        this.userService = userService;
        this.roleService = roleService;
        this.permissionRepository = permissionRepository;
        this.deptRepository = deptRepository;
        this.opLogService = opLogService;
    }

    // ──────────────────── User endpoints ────────────────────

    /**
     * 查询用户列表（分页 + 搜索）
     */
    @GetMapping("/users")
    public Mono<Result<?>> users(
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return Mono.fromCallable(() -> Results.success(userService.listUsers(keyword, page, size)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 用户统计
     */
    @GetMapping("/users/stats")
    public Mono<Result<UserStats>> userStats() {
        return Mono.fromCallable(() -> Results.success(userService.getUserStats()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 单个用户详情
     */
    @GetMapping("/users/{id}")
    public Mono<Result<SysUser>> getUser(@PathVariable Long id) {
        return Mono.fromCallable(() -> Results.success(userService.getUser(id)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 创建用户
     */
    @PostMapping("/users")
    public Mono<Result<SysUser>> createUser(
            @Valid @RequestBody CreateUserRequest req,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return Mono.fromCallable(() ->
                        userService.createUser(req.username(), req.password(),
                                req.realName(), req.deptId(), req.roleCodes()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(),
                                "CREATE_USER", request, saved.getUsername())
                        .thenReturn(Results.success(saved)));
    }

    /**
     * 更新用户（全字段）
     */
    @PutMapping("/users/{id}")
    public Mono<Result<SysUser>> updateUser(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRequest req,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return Mono.fromCallable(() ->
                        userService.updateUser(id, req.realName(), req.deptId(),
                                req.enabled(), req.roleCodes()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(),
                                "UPDATE_USER", request, "userId=" + id)
                        .thenReturn(Results.success(saved)));
    }

    /**
     * 更新用户角色（保持已有端点兼容）
     */
    @PutMapping("/users/{id}/roles")
    public Mono<Result<SysUser>> updateUserRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRolesRequest req,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return Mono.fromCallable(() -> userService.updateUserRoles(id, req.roleCodes()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(),
                                "UPDATE_USER_ROLES", request, "userId=" + id)
                        .thenReturn(Results.success(saved)));
    }

    /**
     * 删除用户
     */
    @DeleteMapping("/users/{id}")
    public Mono<Result<Void>> deleteUser(
            @PathVariable Long id,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return Mono.fromRunnable(() -> userService.deleteUser(id))
                .subscribeOn(Schedulers.boundedElastic())
                .then(opLogService
                        .log(UserContext.userId(), UserContext.username(),
                                "DELETE_USER", request, "userId=" + id)
                        .thenReturn(Results.success()));
    }

    // ──────────────────── Role endpoints ────────────────────

    /**
     * 角色列表（附带 userCount）
     */
    @GetMapping("/roles")
    public Mono<Result<List<RoleDTO>>> roles() {
        return Mono.fromCallable(() -> Results.success(roleService.listRoles()))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 单个角色详情
     */
    @GetMapping("/roles/{id}")
    public Mono<Result<SysRole>> getRole(@PathVariable Long id) {
        return Mono.fromCallable(() -> Results.success(roleService.getRole(id)))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 创建角色
     */
    @PostMapping("/roles")
    public Mono<Result<SysRole>> createRole(
            @Valid @RequestBody CreateRoleRequest req,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return Mono.fromCallable(() ->
                        roleService.createRole(req.code(), req.name(), req.permissionCodes()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(),
                                "CREATE_ROLE", request, saved.getCode())
                        .thenReturn(Results.success(saved)));
    }

    /**
     * 更新角色
     */
    @PutMapping("/roles/{id}")
    public Mono<Result<SysRole>> updateRole(
            @PathVariable Long id,
            @Valid @RequestBody UpdateRoleRequest req,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return Mono.fromCallable(() ->
                        roleService.updateRole(id, req.name(), req.permissionCodes()))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(),
                                "UPDATE_ROLE", request, "roleId=" + id)
                        .thenReturn(Results.success(saved)));
    }

    /**
     * 删除角色
     */
    @DeleteMapping("/roles/{id}")
    public Mono<Result<Void>> deleteRole(
            @PathVariable Long id,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return Mono.fromRunnable(() -> roleService.deleteRole(id))
                .subscribeOn(Schedulers.boundedElastic())
                .then(opLogService
                        .log(UserContext.userId(), UserContext.username(),
                                "DELETE_ROLE", request, "roleId=" + id)
                        .thenReturn(Results.success()));
    }

    // ──────────────────── Permission / Dept (保持已有) ────────────────────

    @GetMapping("/permissions")
    public Mono<Result<List<SysPermission>>> permissions() {
        return Mono.fromCallable(permissionRepository::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .map(Results::success);
    }

    @PostMapping("/permissions")
    public Mono<Result<SysPermission>> createPermission(
            @Valid @RequestBody CreatePermissionRequest req,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return Mono.fromCallable(() -> {
                    if (permissionRepository.findByCode(req.code()).isPresent()) {
                        throw new com.zjl.common.exception.BizException(40000, "权限 code 已存在");
                    }
                    SysPermission p = new SysPermission();
                    p.setCode(req.code());
                    p.setName(req.name());
                    return permissionRepository.save(p);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(),
                                "CREATE_PERMISSION", request, saved.getCode())
                        .thenReturn(Results.success(saved)));
    }

    @GetMapping("/depts")
    public Mono<Result<List<SysDept>>> depts() {
        return Mono.fromCallable(deptRepository::findAll)
                .subscribeOn(Schedulers.boundedElastic())
                .map(Results::success);
    }

    @PostMapping("/depts")
    public Mono<Result<SysDept>> createDept(
            @Valid @RequestBody CreateDeptRequest req,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return Mono.fromCallable(() -> {
                    if (deptRepository.findByName(req.name()).isPresent()) {
                        throw new com.zjl.common.exception.BizException(40000, "部门名称已存在");
                    }
                    SysDept d = new SysDept();
                    d.setName(req.name());
                    d.setParentId(req.parentId());
                    return deptRepository.save(d);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(),
                                "CREATE_DEPT", request, saved.getName())
                        .thenReturn(Results.success(saved)));
    }

    // ──────────────────── Request records ────────────────────

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String password,
            String realName,
            Long deptId,
            Set<String> roleCodes
    ) {}

    public record UpdateUserRequest(
            String realName,
            Long deptId,
            Boolean enabled,
            Set<String> roleCodes
    ) {}

    public record UpdateUserRolesRequest(@NotEmpty Set<String> roleCodes) {}

    public record CreateRoleRequest(
            @NotBlank String code,
            @NotBlank String name,
            Set<String> permissionCodes
    ) {}

    public record UpdateRoleRequest(
            String name,
            Set<String> permissionCodes
    ) {}

    public record CreatePermissionRequest(@NotBlank String code, @NotBlank String name) {}

    public record CreateDeptRequest(@NotBlank String name, Long parentId) {}
}
```

- [ ] **Step 2: 验证编译**

```bash
mvn compile -pl enterprise-gateway-service -am
```

预期：BUILD SUCCESS

- [ ] **Step 3: 启动 Gateway 验证 API**

```bash
mvn spring-boot:run -pl enterprise-gateway-service
```

然后用 curl 验证：
```bash
# 测试用户列表（分页）
curl -u admin:<password> http://localhost:8086/api/system/users?page=1\&size=5

# 测试用户统计
curl -u admin:<password> http://localhost:8086/api/system/users/stats

# 测试角色列表（带 userCount）
curl -u admin:<password> http://localhost:8086/api/system/roles
```

- [ ] **Step 4: Commit**

```bash
git add enterprise-gateway-service/src/main/java/com/zjl/web/SystemAdminController.java
git commit -m "refactor: SystemAdminController 接入 Service 层，补全用户角色 CRUD"
```

---

### Task 7: 前端 API 模块扩展

**Files:**
- Modify: `enterprise-web/src/api/index.js`

- [ ] **Step 1: 添加系统管理 API 函数**

在现有 `getRoles` 和 `isApiAvailable` 之间插入：

```javascript
export function getUser(id) {
  return systemApi.get(`/users/${id}`)
}

export function createUser(body) {
  return systemApi.post('/users', body)
}

export function updateUser(id, body) {
  return systemApi.put(`/users/${id}`, body)
}

export function deleteUser(id) {
  return systemApi.delete(`/users/${id}`)
}

export function getUserStats() {
  return systemApi.get('/users/stats')
}

// ---- Roles ----

export function getRole(id) {
  return systemApi.get(`/roles/${id}`)
}

export function createRole(body) {
  return systemApi.post('/roles', body)
}

export function updateRole(id, body) {
  return systemApi.put(`/roles/${id}`, body)
}

export function deleteRole(id) {
  return systemApi.delete(`/roles/${id}`)
}

export function getPermissions() {
  return systemApi.get('/permissions')
}

export function getDepts() {
  return systemApi.get('/depts')
}
```

> 注：已有的 `getUsers(params)` 和 `getRoles()` 保持不变。

- [ ] **Step 2: Commit**

```bash
git add enterprise-web/src/api/index.js
git commit -m "feat: api/index.js 加用户/角色/权限/部门管理 API 函数"
```

---

### Task 8: Users.vue 替换 mock 为真实 API

**Files:**
- Modify: `enterprise-web/src/pages/admin/Users.vue`

- [ ] **Step 1: 重写 Users.vue**

```vue
<template>
  <div class="admin-view">
    <section class="admin-page-header">
      <div>
        <div class="admin-page-title">用户管理</div>
        <div class="admin-page-subtitle">统一管理后台管理员、知识库运营人员和普通业务用户。</div>
      </div>
      <div class="admin-actions">
        <el-button type="primary" @click="openCreate">新增用户</el-button>
      </div>
    </section>

    <section class="admin-grid-4">
      <article v-for="item in stats" :key="item.label" class="admin-stat">
        <div class="admin-stat-value">{{ item.value }}</div>
        <div class="admin-stat-label">{{ item.label }}</div>
        <div class="admin-stat-meta">{{ item.meta }}</div>
      </article>
    </section>

    <section class="admin-table-card">
      <div class="admin-toolbar">
        <div>
          <div class="admin-toolbar-title">用户清单</div>
          <div class="admin-toolbar-subtitle">查看账号、部门、角色和启停状态。</div>
        </div>
        <el-input v-model="keyword" placeholder="搜索用户名或姓名" style="width:200px" clearable @input="onSearch" />
      </div>
      <el-table :data="users" v-loading="loading">
        <el-table-column prop="id" label="ID" width="80" />
        <el-table-column prop="username" label="用户名" min-width="120" />
        <el-table-column prop="realName" label="姓名" min-width="120" />
        <el-table-column label="部门" min-width="120">
          <template #default="{ row }">{{ row.dept?.name || '-' }}</template>
        </el-table-column>
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">{{ row.roles?.map(r => r.name).join('、') || '-' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" size="small">
              {{ row.enabled ? '启用' : '禁用' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="200" fixed="right">
          <template #default="{ row }">
            <el-button size="small" @click="openEdit(row)">编辑</el-button>
            <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      <div style="display:flex;justify-content:flex-end;margin-top:16px">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10,20,50]"
          layout="total,sizes,prev,pager,next"
          @size-change="loadUsers"
          @current-change="loadUsers"
        />
      </div>
    </section>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑用户' : '新增用户'" width="480px" @closed="resetForm">
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="用户名" required>
          <el-input v-model="form.username" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item v-if="!editingId" label="密码" required>
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="部门">
          <el-select v-model="form.deptId" clearable placeholder="请选择部门">
            <el-option v-for="d in depts" :key="d.id" :label="d.name" :value="d.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="form.roleCodes" multiple placeholder="请选择角色">
            <el-option v-for="r in allRoles" :key="r.code" :label="r.name" :value="r.code" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="editingId" label="启用">
          <el-switch v-model="form.enabled" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, nextTick } from 'vue'
import { getUsers, getUserStats, createUser, updateUser, deleteUser, getRoles, getDepts } from '../../api/index.js'
import { ElMessage, ElMessageBox } from 'element-plus'

const users = ref([])
const loading = ref(false)
const keyword = ref('')
const currentPage = ref(1)
const pageSize = ref(20)
const total = ref(0)
const stats = computed(() => [
  { label: '总用户数', value: _stats.value.total, meta: '后台当前管理的账号总数' },
  { label: '启用账号', value: _stats.value.enabled, meta: '可正常登录与使用' },
  { label: '管理员', value: _stats.value.admin, meta: '具备后台操作权限' },
  { label: '停用账号', value: _stats.value.disabled, meta: '已冻结或待恢复' }
])

const _stats = ref({ total: 0, enabled: 0, admin: 0, disabled: 0 })

let timer = null
function onSearch() {
  clearTimeout(timer)
  timer = setTimeout(() => { currentPage.value = 1; loadUsers() }, 300)
}

async function loadUsers() {
  loading.value = true
  try {
    const { data: res } = await getUsers({ keyword: keyword.value, page: currentPage.value, size: pageSize.value })
    if (res.code === 0) {
      users.value = res.data.records
      total.value = res.data.total
    }
  } catch (e) {
    ElMessage.error('加载用户列表失败')
  } finally { loading.value = false }
}

async function loadStats() {
  try {
    const { data: res } = await getUserStats()
    if (res.code === 0) _stats.value = res.data
  } catch { /* ignore */ }
}

const depts = ref([])
const allRoles = ref([])

async function loadOptions() {
  try {
    const [deptRes, roleRes] = await Promise.all([getDepts(), getRoles()])
    if (deptRes.data.code === 0) depts.value = deptRes.data.data
    if (roleRes.data.code === 0) allRoles.value = roleRes.data.data
  } catch { /* ignore */ }
}

// 新增 / 编辑
const dialogVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = ref({ username: '', password: '', realName: '', deptId: null, roleCodes: [], enabled: true })

function openCreate() {
  editingId.value = null
  form.value = { username: '', password: '', realName: '', deptId: null, roleCodes: [], enabled: true }
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = {
    username: row.username,
    password: '',
    realName: row.realName || '',
    deptId: row.dept?.id || null,
    roleCodes: row.roles?.map(r => r.code) || [],
    enabled: row.enabled
  }
  dialogVisible.value = true
}

function resetForm() {
  editingId.value = null
  form.value = { username: '', password: '', realName: '', deptId: null, roleCodes: [], enabled: true }
}

async function handleSubmit() {
  submitting.value = true
  try {
    const body = {
      username: form.value.username,
      password: form.value.password,
      realName: form.value.realName || undefined,
      deptId: form.value.deptId || undefined,
      roleCodes: form.value.roleCodes.length ? form.value.roleCodes : undefined
    }
    if (editingId.value) {
      body.enabled = form.value.enabled
      await updateUser(editingId.value, body)
      ElMessage.success('更新成功')
    } else {
      await createUser(body)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadUsers()
    loadStats()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除用户「${row.username}」吗？`, '提示', { type: 'warning' })
    await deleteUser(row.id)
    ElMessage.success('删除成功')
    loadUsers()
    loadStats()
  } catch { /* cancelled or error */ }
}

onMounted(() => { loadUsers(); loadStats(); loadOptions() })
</script>
```

- [ ] **Step 2: 验证前端编译**

```bash
cd enterprise-web && npm run build
```

预期：无编译错误

- [ ] **Step 3: Commit**

```bash
git add enterprise-web/src/pages/admin/Users.vue
git commit -m "feat: Users.vue 替换 mock 为真实 API，含分页搜索增删改"
```

---

### Task 9: Roles.vue 替换 mock 为真实 API

**Files:**
- Modify: `enterprise-web/src/pages/admin/Roles.vue`

- [ ] **Step 1: 重写 Roles.vue**

```vue
<template>
  <div>
    <div style="display:flex;justify-content:space-between;margin-bottom:16px">
      <span style="font-size:16px;font-weight:600">角色管理</span>
      <el-button type="primary" @click="openCreate">新增角色</el-button>
    </div>
    <el-table :data="roles" stripe v-loading="loading">
      <el-table-column prop="code" label="角色编码" width="140" />
      <el-table-column prop="name" label="角色名称" />
      <el-table-column label="权限数" width="80">
        <template #default="{ row }">{{ row.permissions?.length || 0 }}</template>
      </el-table-column>
      <el-table-column prop="userCount" label="用户数" width="80" />
      <el-table-column label="操作" width="160">
        <template #default="{ row }">
          <el-button size="small" @click="openEdit(row)">编辑</el-button>
          <el-button size="small" type="danger" @click="handleDelete(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑角色' : '新增角色'" width="460px" @closed="resetForm">
      <el-form ref="formRef" :model="form" label-width="80px">
        <el-form-item label="角色编码" required>
          <el-input v-model="form.code" :disabled="!!editingId" />
        </el-form-item>
        <el-form-item label="角色名称" required>
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="权限">
          <el-select v-model="form.permissionCodes" multiple placeholder="请选择权限">
            <el-option v-for="p in permissions" :key="p.code" :label="p.name" :value="p.code" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { getRoles, createRole, updateRole, deleteRole, getPermissions } from '../../api/index.js'
import { ElMessage, ElMessageBox } from 'element-plus'

const roles = ref([])
const loading = ref(false)

async function loadRoles() {
  loading.value = true
  try {
    const { data: res } = await getRoles()
    if (res.code === 0) roles.value = res.data
  } catch { ElMessage.error('加载角色列表失败') }
  finally { loading.value = false }
}

const permissions = ref([])

async function loadPermissions() {
  try {
    const { data: res } = await getPermissions()
    if (res.code === 0) permissions.value = res.data
  } catch { /* ignore */ }
}

// 新增 / 编辑
const dialogVisible = ref(false)
const editingId = ref(null)
const submitting = ref(false)
const formRef = ref(null)
const form = ref({ code: '', name: '', permissionCodes: [] })

function openCreate() {
  editingId.value = null
  form.value = { code: '', name: '', permissionCodes: [] }
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  form.value = {
    code: row.code,
    name: row.name,
    permissionCodes: row.permissions?.map(p => p.code) || []
  }
  dialogVisible.value = true
}

function resetForm() {
  editingId.value = null
  form.value = { code: '', name: '', permissionCodes: [] }
}

async function handleSubmit() {
  submitting.value = true
  try {
    const body = {
      code: form.value.code,
      name: form.value.name,
      permissionCodes: form.value.permissionCodes.length ? form.value.permissionCodes : undefined
    }
    if (editingId.value) {
      await updateRole(editingId.value, { name: body.name, permissionCodes: body.permissionCodes })
      ElMessage.success('更新成功')
    } else {
      await createRole(body)
      ElMessage.success('创建成功')
    }
    dialogVisible.value = false
    loadRoles()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  } finally { submitting.value = false }
}

async function handleDelete(row) {
  try {
    await ElMessageBox.confirm(`确定删除角色「${row.name}」吗？`, '提示', { type: 'warning' })
    await deleteRole(row.id)
    ElMessage.success('删除成功')
    loadRoles()
  } catch { /* cancelled or error */ }
}

onMounted(() => { loadRoles(); loadPermissions() })
</script>
```

- [ ] **Step 2: 验证前端编译**

```bash
cd enterprise-web && npm run build
```

预期：无编译错误

- [ ] **Step 3: Commit**

```bash
git add enterprise-web/src/pages/admin/Roles.vue
git commit -m "feat: Roles.vue 替换 mock 为真实 API，含增删改"
```

---

### Task 10: 全链路验证

- [ ] **Step 1: 启动 Gateway**

```bash
mvn spring-boot:run -pl enterprise-gateway-service
```

验证 API：
```bash
# 用户
curl -H "X-Is-Admin:true" http://localhost:8086/api/system/users?page=1\&size=5
curl -H "X-Is-Admin:true" http://localhost:8086/api/system/users/stats
curl -H "X-Is-Admin:true" http://localhost:8086/api/system/users/1
# 角色
curl -H "X-Is-Admin:true" http://localhost:8086/api/system/roles
# 权限
curl -H "X-Is-Admin:true" http://localhost:8086/api/system/permissions
# 部门
curl -H "X-Is-Admin:true" http://localhost:8086/api/system/depts
```

全部应返回 `"code":0`。

- [ ] **Step 2: 启动前端**

```bash
cd enterprise-web && npm run dev
```

浏览器打开 → 登录 → 管理后台 → 用户管理 → 验证：
1. 表格显示真实数据
2. 搜索框输入关键词，回车或 300ms 后表格更新
3. 翻页正常
4. 统计卡片数字与实际一致
5. 新增用户弹窗 → 填写并提交 → 表格刷新
6. 编辑 → 修改姓名/部门/角色 → 保存 → 表格刷新
7. 删除 → 确认 → 表格刷新

角色管理 → 验证：
1. 表格显示角色列表 + userCount
2. 新增/编辑/删除正常

- [ ] **Step 3: Commit（如有问题修复）**

```bash
git add -A
git commit -m "fix: 全链路验证问题修复"
```
