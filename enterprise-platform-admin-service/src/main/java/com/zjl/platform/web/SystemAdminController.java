package com.zjl.platform.web;

import com.zjl.common.exception.BizException;
import com.zjl.common.response.PageResult;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.platform.dto.RoleDTO;
import com.zjl.platform.dto.UserInfoDTO;
import com.zjl.platform.entity.*;
import com.zjl.platform.mapper.SysDeptMapper;
import com.zjl.platform.mapper.SysPermissionMapper;
import com.zjl.platform.service.OpLogService;
import com.zjl.platform.service.RoleService;
import com.zjl.platform.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Validated
@RestController
@RequestMapping("/api/system")
public class SystemAdminController {

    private final UserService userService;
    private final RoleService roleService;
    private final SysPermissionMapper permissionMapper;
    private final SysDeptMapper deptMapper;
    private final OpLogService opLogService;

    public SystemAdminController(UserService userService, RoleService roleService,
                                  SysPermissionMapper permissionMapper, SysDeptMapper deptMapper,
                                  OpLogService opLogService) {
        this.userService = userService;
        this.roleService = roleService;
        this.permissionMapper = permissionMapper;
        this.deptMapper = deptMapper;
        this.opLogService = opLogService;
    }

    // ──────────────────── User endpoints ────────────────────

    @GetMapping("/users")
    public Result<PageResult<SysUser>> users(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        return Results.success(userService.listUsers(keyword, page, size));
    }

    @GetMapping("/users/stats")
    public Result<UserService.UserStats> userStats(HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        return Results.success(userService.getUserStats());
    }

    @GetMapping("/users/{id}")
    public Result<SysUser> getUser(@PathVariable Long id, HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        return Results.success(userService.getUser(id));
    }

    @PostMapping("/users")
    public Result<SysUser> createUser(@Valid @RequestBody CreateUserRequest req,
                                       HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        SysUser saved = userService.createUser(req.username(), req.password(),
                req.realName(), req.deptId(), req.roleCodes());
        opLogService.log(UserContext.current().userId(), "admin", "CREATE_USER",
                request.getMethod(), request.getRequestURI(), saved.getUsername());
        return Results.success(saved);
    }

    @PutMapping("/users/{id}")
    public Result<SysUser> updateUser(@PathVariable Long id,
                                       @Valid @RequestBody UpdateUserRequest req,
                                       HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        SysUser saved = userService.updateUser(id, req.realName(), req.deptId(),
                req.enabled(), req.roleCodes());
        opLogService.log(UserContext.current().userId(), "admin", "UPDATE_USER",
                request.getMethod(), request.getRequestURI(), "userId=" + id);
        return Results.success(saved);
    }

    @PutMapping("/users/{id}/roles")
    public Result<SysUser> updateUserRoles(@PathVariable Long id,
                                            @Valid @RequestBody UpdateUserRolesRequest req,
                                            HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        SysUser saved = userService.updateUserRoles(id, req.roleCodes());
        opLogService.log(UserContext.current().userId(), "admin", "UPDATE_USER_ROLES",
                request.getMethod(), request.getRequestURI(), "userId=" + id);
        return Results.success(saved);
    }

    @DeleteMapping("/users/{id}")
    public Result<Void> deleteUser(@PathVariable Long id, HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        userService.deleteUser(id);
        opLogService.log(UserContext.current().userId(), "admin", "DELETE_USER",
                request.getMethod(), request.getRequestURI(), "userId=" + id);
        return Results.success();
    }

    // ──────────────────── User batch/search ────────────────────

    @GetMapping("/users/batch")
    public Result<Map<Long, UserInfoDTO>> batchUsers(@RequestParam("ids") List<Long> ids) {
        log.debug("批量查询用户: ids={}", ids);
        return Results.success(userService.batchGetUsers(ids));
    }

    @GetMapping("/users/search")
    public Result<List<UserInfoDTO>> searchUsers(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "limit", defaultValue = "50") int limit) {
        log.debug("搜索用户: keyword={}", keyword);
        return Results.success(userService.searchUsers(keyword, limit));
    }

    // ──────────────────── Role endpoints ────────────────────

    @GetMapping("/roles")
    public Result<List<RoleDTO>> roles(HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        return Results.success(roleService.listRoles());
    }

    @GetMapping("/roles/{id}")
    public Result<SysRole> getRole(@PathVariable Long id, HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        return Results.success(roleService.getRole(id));
    }

    @PostMapping("/roles")
    public Result<SysRole> createRole(@Valid @RequestBody CreateRoleRequest req,
                                       HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        SysRole saved = roleService.createRole(req.code(), req.name(), req.permissionCodes());
        opLogService.log(UserContext.current().userId(), "admin", "CREATE_ROLE",
                request.getMethod(), request.getRequestURI(), saved.getCode());
        return Results.success(saved);
    }

    @PutMapping("/roles/{id}")
    public Result<SysRole> updateRole(@PathVariable Long id,
                                       @Valid @RequestBody UpdateRoleRequest req,
                                       HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        SysRole saved = roleService.updateRole(id, req.name(), req.permissionCodes());
        opLogService.log(UserContext.current().userId(), "admin", "UPDATE_ROLE",
                request.getMethod(), request.getRequestURI(), "roleId=" + id);
        return Results.success(saved);
    }

    @DeleteMapping("/roles/{id}")
    public Result<Void> deleteRole(@PathVariable Long id, HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        roleService.deleteRole(id);
        opLogService.log(UserContext.current().userId(), "admin", "DELETE_ROLE",
                request.getMethod(), request.getRequestURI(), "roleId=" + id);
        return Results.success();
    }

    // ──────────────────── Permission endpoints ────────────────────

    @GetMapping("/permissions")
    public Result<List<SysPermission>> permissions(HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        return Results.success(permissionMapper.selectList(null));
    }

    @PostMapping("/permissions")
    public Result<SysPermission> createPermission(@Valid @RequestBody CreatePermissionRequest req,
                                                    HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        if (permissionMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysPermission>()
                        .eq(SysPermission::getCode, req.code())) != null) {
            throw new BizException(40000, "权限 code 已存在");
        }
        SysPermission p = new SysPermission();
        p.setCode(req.code());
        p.setName(req.name());
        permissionMapper.insert(p);
        opLogService.log(UserContext.current().userId(), "admin", "CREATE_PERMISSION",
                request.getMethod(), request.getRequestURI(), p.getCode());
        return Results.success(p);
    }

    // ──────────────────── Dept endpoints ────────────────────

    @GetMapping("/depts")
    public Result<List<SysDept>> depts(HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        return Results.success(deptMapper.selectList(null));
    }

    @PostMapping("/depts")
    public Result<SysDept> createDept(@Valid @RequestBody CreateDeptRequest req,
                                       HttpServletRequest request) {
        log.info("管理员操作: {} {}, operatorId={}", request.getMethod(), request.getRequestURI(), UserContext.current().userId());
        if (deptMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysDept>()
                        .eq(SysDept::getName, req.name())) != null) {
            throw new BizException(40000, "部门名称已存在");
        }
        SysDept d = new SysDept();
        d.setName(req.name());
        d.setParentId(req.parentId());
        deptMapper.insert(d);
        opLogService.log(UserContext.current().userId(), "admin", "CREATE_DEPT",
                request.getMethod(), request.getRequestURI(), d.getName());
        return Results.success(d);
    }

    // ──────────────────── Logs endpoints ────────────────────

    @GetMapping("/logs")
    public Result<PageResult<SysOpLog>> logs(
            @RequestParam(value = "keyword", defaultValue = "") String keyword,
            @RequestParam(value = "action", defaultValue = "") String action,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Results.success(opLogService.searchLogs(keyword, action, page, size));
    }

    // ──────────────────── Request records ────────────────────

    public record CreateUserRequest(
            @NotBlank String username,
            @NotBlank String password,
            String realName,
            Long deptId,
            Set<String> roleCodes) {}

    public record UpdateUserRequest(
            String realName, Long deptId, Boolean enabled, Set<String> roleCodes) {}

    public record UpdateUserRolesRequest(@NotEmpty Set<String> roleCodes) {}

    public record CreateRoleRequest(
            @NotBlank String code, @NotBlank String name, Set<String> permissionCodes) {}

    public record UpdateRoleRequest(String name, Set<String> permissionCodes) {}

    public record CreatePermissionRequest(@NotBlank String code, @NotBlank String name) {}

    public record CreateDeptRequest(@NotBlank String name, Long parentId) {}
}
