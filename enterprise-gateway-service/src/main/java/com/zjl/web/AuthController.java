package com.zjl.web;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.domain.SysRole;
import com.zjl.domain.SysUser;
import com.zjl.repository.SysUserRepository;
import com.zjl.security.JwtUtil;
import com.zjl.security.TokenBlacklistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;

/**
 * 认证相关接口（登录、退出）。
 */
@Validated
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /**
     * 用户仓库
     */
    private final SysUserRepository userRepository;

    /**
     * 密码编码器
     */
    private final BCryptPasswordEncoder passwordEncoder;

    /**
     * JWT 服务（签发、解析）
     */
    private final JwtUtil jwt;

    /**
     * token 黑名单服务（用于退出后使 token 失效）
     */
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * 构造器注入
     *
     * @param userRepository 用户仓库
     * @param passwordEncoder 密码编码器
     * @param jwtService JWT 服务
     * @param tokenBlacklistService token 黑名单服务
     */
    public AuthController(
            SysUserRepository userRepository,
            BCryptPasswordEncoder passwordEncoder,
            JwtUtil jwtService,
            TokenBlacklistService tokenBlacklistService
    )
    {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwt = jwtService;
        this.tokenBlacklistService = tokenBlacklistService;
    }

    /**
     * 登录请求
     *
     * @param username 用户名
     * @param password 密码
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}

    /**
     * 登录响应（供前端写入 localStorage）
     *
     * @param token 访问令牌
     * @param userId 用户 ID
     * @param username 用户名
     * @param realName 姓名
     * @param deptId 部门 ID
     * @param isAdmin 是否具备管理员角色（角色 code 为 admin，不区分大小写）
     */
    public record LoginResponse(
            String token,
            Long userId,
            String username,
            String realName,
            Long deptId,
            boolean isAdmin
    ) {}

    /**
     * 登录接口：校验用户名密码并签发 JWT
     *
     * @param req 登录请求
     * @return token
     */
    @PostMapping("/login")
    public Mono<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest req) {
        return Mono.fromCallable(() -> userRepository.findByUsername(req.username()).orElse(null))
                // 数据库查询放到弹性线程池，避免阻塞 Netty IO 线程
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    // 用户不存在或已被禁用
                    if (user == null || !user.isEnabled()) {
                        return Mono.error(new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误"));
                    }
                    // BCrypt 密文比对
                    if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                        return Mono.error(new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误"));
                    }
                    // 从用户角色和权限生成 JWT authorities 列表
                    Map<String, Object> claims = new HashMap<>();
                    claims.put("authorities", authoritiesOf(user));
                    // 签发 JWT
                    String token = jwt.issueToken(user.getId(), user.getUsername(), claims);
                    Long deptId = user.getDept() != null ? user.getDept().getId() : null;
                    boolean isAdmin = user.getRoles().stream()
                            .anyMatch(r -> "admin".equalsIgnoreCase(r.getCode()));
                    return Mono.just(Results.success(new LoginResponse(
                            token,
                            user.getId(),
                            user.getUsername(),
                            user.getRealName(),
                            deptId,
                            isAdmin
                    )));
                });
    }

    /**
     * 退出接口：将当前 token 置入黑名单
     *
     * @param authorization Authorization 头（Bearer token）
     * @return 成功响应
     */
    @PostMapping("/logout")
    public Mono<Result<Void>> logout(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = extractBearerToken(authorization);
        // 无 token 也返回成功，幂等
        if (!StringUtils.hasText(token)) {
            return Mono.just(Results.success());
        }
        return tokenBlacklistService.blacklist(token)
                .thenReturn(Results.success());
    }

    /**
     * 提取 Bearer token。
     *
     * @param authorization Authorization 头
     * @return token（可能为空）
     */
    private static String extractBearerToken(String authorization) {
        if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
            return null;
        }
        // 去掉前缀 "Bearer "，返回纯 token
        return authorization.substring(7);
    }

    /**
     * 从用户角色/权限生成 JWT authorities。
     *
     * @param user 用户
     * @return authorities 列表
     */
    private static List<String> authoritiesOf(SysUser user) {
        List<String> result = new ArrayList<>();
        for (SysRole role : user.getRoles()) {
            // 角色编码转大写并加 ROLE_ 前缀，与 @PreAuthorize("hasRole('ADMIN')") 一致
            result.add("ROLE_" + role.getCode().toUpperCase());
            // 角色下的每个权限直接放入列表，如 PERM_doc_delete
            role.getPermissions().forEach(p -> result.add(p.getCode()));
        }
        return result;
    }

}

