package com.zjl.web;

import com.zjl.common.exception.BizException;
import com.zjl.common.response.PageResult;
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
import com.zjl.service.RoleDTO;
import com.zjl.service.RoleService;
import com.zjl.service.UserService;
import com.zjl.service.UserService.UserStats;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public Mono<Result<PageResult<SysUser>>> users(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size
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

    // ──────────────────── Permission / Dept (保持已有逻辑) ────────────────────

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
                        throw new BizException(40000, "权限 code 已存在");
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
                        throw new BizException(40000, "部门名称已存在");
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
