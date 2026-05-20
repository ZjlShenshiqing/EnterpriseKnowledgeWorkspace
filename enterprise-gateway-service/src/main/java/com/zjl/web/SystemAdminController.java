package com.zjl.web;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.common.exception.BizException;
import com.zjl.domain.SysDept;
import com.zjl.domain.SysPermission;
import com.zjl.domain.SysRole;
import com.zjl.domain.SysUser;
import com.zjl.repository.SysDeptRepository;
import com.zjl.repository.SysPermissionRepository;
import com.zjl.repository.SysRoleRepository;
import com.zjl.repository.SysUserRepository;
import com.zjl.security.UserContext;
import com.zjl.service.OpLogService;
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
import java.util.stream.Collectors;

/**
 * 系统管理（RBAC）后台接口
 *
 * <p>约束：仅允许管理员访问（hasRole('ADMIN')）。</p>
 */
@Validated
@RestController
@RequestMapping("/api/system")
@PreAuthorize("hasRole('ADMIN')")
public class SystemAdminController {

    /**
     * 用户仓库
     */
    private final SysUserRepository userRepository;
    /**
     * 角色仓库
     */
    private final SysRoleRepository roleRepository;
    /**
     * 权限仓库
     */
    private final SysPermissionRepository permissionRepository;
    /**
     * 部门仓库
     */
    private final SysDeptRepository deptRepository;
    /**
     * 密码编码器
     */
    private final BCryptPasswordEncoder passwordEncoder;
    /**
     * 操作日志服务（用于关键管理操作审计）
     */
    private final OpLogService opLogService;

    /**
     * 构造器注入
     *
     * @param userRepository 用户仓库
     * @param roleRepository 角色仓库
     * @param permissionRepository 权限仓库
     * @param deptRepository 部门仓库
     * @param passwordEncoder 密码编码器
     * @param opLogService 操作日志服务
     */
    public SystemAdminController(
            SysUserRepository userRepository,
            SysRoleRepository roleRepository,
            SysPermissionRepository permissionRepository,
            SysDeptRepository deptRepository,
            BCryptPasswordEncoder passwordEncoder,
            OpLogService opLogService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.deptRepository = deptRepository;
        this.passwordEncoder = passwordEncoder;
        this.opLogService = opLogService;
    }

    /**
     * 创建用户请求
     *
     * @param username 用户名
     * @param password 明文密码（仅在创建时使用；持久化存储为 hash）
     * @param roleCodes 角色编码集合
     */
    public record CreateUserRequest(@NotBlank String username, @NotBlank String password, Set<String> roleCodes) {}
    
    /**
     * 更新用户角色请求
     *
     * @param roleCodes 角色编码集合
     */
    public record UpdateUserRolesRequest(@NotEmpty Set<String> roleCodes) {}

    /**
     * 查询用户列表
     *
     * @return 用户列表
     */
    @GetMapping("/users")
    public Mono<Result<List<SysUser>>> users() {
        return Mono.fromCallable(userRepository::findAll)
                // 数据库查询放到弹性线程池，避免阻塞 Netty IO 线程
                .subscribeOn(Schedulers.boundedElastic())
                .map(list -> Results.success(list));
    }

    /**
     * 创建用户（可选绑定角色）
     *
     * @param req 请求体
     * @param request 原始请求（用于操作日志）
     * @return 创建后的用户
     */
    @PostMapping("/users")
    public Mono<Result<SysUser>> createUser(@Valid @RequestBody CreateUserRequest req, org.springframework.http.server.reactive.ServerHttpRequest request) {
        return Mono.fromCallable(() -> {
                    // 校验用户名唯一性
                    if (userRepository.findByUsername(req.username()).isPresent()) {
                        throw new BizException(40000, "用户名已存在");
                    }
                    // 构建用户实体
                    SysUser u = new SysUser();
                    u.setUsername(req.username());
                    // BCrypt 加密存储密码，不可逆
                    u.setPasswordHash(passwordEncoder.encode(req.password()));
                    // 可选：创建时直接绑定角色
                    if (req.roleCodes() != null && !req.roleCodes().isEmpty()) {
                        Set<SysRole> roles = req.roleCodes().stream()
                                .map(code -> roleRepository.findByCode(code).orElseThrow(() -> new BizException(40000, "角色不存在: " + code)))
                                .collect(Collectors.toSet());
                        u.setRoles(roles);
                    }
                    return userRepository.save(u);
                })
                .subscribeOn(Schedulers.boundedElastic())
                // 异步写入审计日志，不阻塞响应
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(), "CREATE_USER", request, saved.getUsername())
                        .thenReturn(Results.success(saved)));
    }

    /**
     * 更新用户角色集合
     *
     * @param id 用户 id
     * @param req 请求体
     * @param request 原始请求（用于操作日志）
     * @return 更新后的用户
     */
    @PutMapping("/users/{id}/roles")
    public Mono<Result<SysUser>> updateUserRoles(
            @PathVariable Long id,
            @Valid @RequestBody UpdateUserRolesRequest req,
            org.springframework.http.server.reactive.ServerHttpRequest request
    ) {
        return Mono.fromCallable(() -> {
                    // 查询用户，不存在则抛异常
                    SysUser u = userRepository.findById(id).orElseThrow(() -> new BizException(40400, "用户不存在"));
                    // 将角色编码集合转为 SysRole 实体集合
                    Set<SysRole> roles = req.roleCodes().stream()
                            .map(code -> roleRepository.findByCode(code).orElseThrow(() -> new BizException(40000, "角色不存在: " + code)))
                            .collect(Collectors.toSet());
                    // 全量替换用户角色
                    u.setRoles(roles);
                    return userRepository.save(u);
                })
                .subscribeOn(Schedulers.boundedElastic())
                // 异步写入审计日志
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(), "UPDATE_USER_ROLES", request, "userId=" + id)
                        .thenReturn(Results.success(saved)));
    }

    /**
     * 创建角色请求
     *
     * @param code 角色编码
     * @param name 角色名称
     * @param permissionCodes 权限编码集合
     */
    public record CreateRoleRequest(@NotBlank String code, @NotBlank String name, Set<String> permissionCodes) {}

    /**
     * 查询角色列表
     *
     * @return 角色列表
     */
    @GetMapping("/roles")
    public Mono<Result<List<SysRole>>> roles() {
        return Mono.fromCallable(roleRepository::findAll)
                // 数据库查询放到弹性线程池
                .subscribeOn(Schedulers.boundedElastic())
                .map(list -> Results.success(list));
    }

    /**
     * 创建角色（可选绑定权限）。
     *
     * @param req 请求体
     * @param request 原始请求（用于操作日志）
     * @return 创建后的角色
     */
    @PostMapping("/roles")
    public Mono<Result<SysRole>> createRole(@Valid @RequestBody CreateRoleRequest req, org.springframework.http.server.reactive.ServerHttpRequest request) {
        return Mono.fromCallable(() -> {
                    // 校验角色编码唯一性
                    if (roleRepository.findByCode(req.code()).isPresent()) {
                        throw new BizException(40000, "角色 code 已存在");
                    }
                    // 构建角色实体
                    SysRole r = new SysRole();
                    r.setCode(req.code());
                    r.setName(req.name());
                    // 可选：创建时直接绑定权限
                    if (req.permissionCodes() != null && !req.permissionCodes().isEmpty()) {
                        Set<SysPermission> perms = req.permissionCodes().stream()
                                .map(code -> permissionRepository.findByCode(code).orElseThrow(() -> new BizException(40000, "权限不存在: " + code)))
                                .collect(Collectors.toSet());
                        r.setPermissions(perms);
                    }
                    return roleRepository.save(r);
                })
                .subscribeOn(Schedulers.boundedElastic())
                // 异步写入审计日志
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(), "CREATE_ROLE", request, saved.getCode())
                        .thenReturn(Results.success(saved)));
    }

    /**
     * 创建权限请求
     *
     * @param code 权限编码
     * @param name 权限名称
     */
    public record CreatePermissionRequest(@NotBlank String code, @NotBlank String name) {}

    /**
     * 查询权限列表
     *
     * @return 权限列表
     */
    @GetMapping("/permissions")
    public Mono<Result<List<SysPermission>>> permissions() {
        return Mono.fromCallable(permissionRepository::findAll)
                // 数据库查询放到弹性线程池
                .subscribeOn(Schedulers.boundedElastic())
                .map(list -> Results.success(list));
    }

    /**
     * 创建权限
     *
     * @param req 请求体
     * @param request 原始请求（用于操作日志）
     * @return 创建后的权限
     */
    @PostMapping("/permissions")
    public Mono<Result<SysPermission>> createPermission(@Valid @RequestBody CreatePermissionRequest req, org.springframework.http.server.reactive.ServerHttpRequest request) {
        return Mono.fromCallable(() -> {
                    // 校验权限编码唯一性
                    if (permissionRepository.findByCode(req.code()).isPresent()) {
                        throw new BizException(40000, "权限 code 已存在");
                    }
                    // 构建权限实体
                    SysPermission p = new SysPermission();
                    p.setCode(req.code());
                    p.setName(req.name());
                    return permissionRepository.save(p);
                })
                .subscribeOn(Schedulers.boundedElastic())
                // 异步写入审计日志
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(), "CREATE_PERMISSION", request, saved.getCode())
                        .thenReturn(Results.success(saved)));
    }

    /**
     * 创建部门请求
     *
     * @param name 部门名称
     * @param parentId 父部门 id
     */
    public record CreateDeptRequest(@NotBlank String name, Long parentId) {}

    /**
     * 查询部门列表
     *
     * @return 部门列表
     */
    @GetMapping("/depts")
    public Mono<Result<List<SysDept>>> depts() {
        return Mono.fromCallable(deptRepository::findAll)
                // 数据库查询放到弹性线程池
                .subscribeOn(Schedulers.boundedElastic())
                .map(list -> Results.success(list));
    }

    /**
     * 创建部门
     *
     * @param req 请求体
     * @param request 原始请求（用于操作日志）
     * @return 创建后的部门
     */
    @PostMapping("/depts")
    public Mono<Result<SysDept>> createDept(@Valid @RequestBody CreateDeptRequest req, org.springframework.http.server.reactive.ServerHttpRequest request) {
        return Mono.fromCallable(() -> {
                    // 校验部门名称唯一性
                    if (deptRepository.findByName(req.name()).isPresent()) {
                        throw new BizException(40000, "部门名称已存在");
                    }
                    // 构建部门实体，支持树形结构（parentId）
                    SysDept d = new SysDept();
                    d.setName(req.name());
                    d.setParentId(req.parentId());
                    return deptRepository.save(d);
                })
                .subscribeOn(Schedulers.boundedElastic())
                // 异步写入审计日志
                .flatMap(saved -> opLogService
                        .log(UserContext.userId(), UserContext.username(), "CREATE_DEPT", request, saved.getName())
                        .thenReturn(Results.success(saved)));
    }
}

