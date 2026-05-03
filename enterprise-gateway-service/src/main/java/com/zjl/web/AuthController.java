package com.zjl.web;

import com.zjl.common.response.ApiResponse;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.common.trace.TraceIdHolder;
import com.zjl.domain.SysRole;
import com.zjl.domain.SysUser;
import com.zjl.repository.SysUserRepository;
import com.zjl.security.JwtUtil;
import com.zjl.security.TokenBlacklistService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.MDC;
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
     * JWT 服务（签发、解析）。
     */
    private final JwtUtil jwt;

    /**
     * token 黑名单服务（用于退出后使 token 失效）。
     */
    private final TokenBlacklistService tokenBlacklistService;

    /**
     * 构造器注入。
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
     * 登录请求。
     *
     * @param username 用户名
     * @param password 密码
     */
    public record LoginRequest(@NotBlank String username, @NotBlank String password) {}
    /**
     * 登录响应。
     *
     * @param token JWT
     */
    public record LoginResponse(String token) {}

    /**
     * 登录接口：校验用户名密码并签发 JWT。
     *
     * @param req 登录请求
     * @return token
     */
    @PostMapping("/login")
    public Mono<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest req) {
        return Mono.fromCallable(() -> userRepository.findByUsername(req.username()).orElse(null))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null || !user.isEnabled()) {
                        return Mono.error(new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误"));
                    }
                    if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                        return Mono.error(new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误"));
                    }
                    Map<String, Object> claims = new HashMap<>();
                    claims.put("authorities", authoritiesOf(user));
                    String token = jwt.issueToken(user.getId(), user.getUsername(), claims);
                    return Mono.just(ApiResponse.success(new LoginResponse(token), traceId()));
                });
    }

    /**
     * 退出接口：将当前 token 置入黑名单。
     *
     * @param authorization Authorization 头（Bearer token）
     * @return 成功响应
     */
    @PostMapping("/logout")
    public Mono<ApiResponse<Void>> logout(@RequestHeader(name = HttpHeaders.AUTHORIZATION, required = false) String authorization) {
        String token = extractBearerToken(authorization);
        if (!StringUtils.hasText(token)) {
            return Mono.just(ApiResponse.success(traceId()));
        }
        return tokenBlacklistService.blacklist(token)
                .thenReturn(ApiResponse.success(traceId()));
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
            result.add("ROLE_" + role.getCode());
            role.getPermissions().forEach(p -> result.add(p.getCode()));
        }
        return result;
    }

    /**
     * 从 MDC 获取 traceId。
     *
     * @return traceId
     */
    private String traceId() {
        return MDC.get(TraceIdHolder.key());
    }
}

