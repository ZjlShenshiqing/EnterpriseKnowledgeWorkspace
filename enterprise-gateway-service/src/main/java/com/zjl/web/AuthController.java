package com.zjl.web;

import cn.dev33.satoken.stp.StpUtil;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.domain.SysUser;
import com.zjl.repository.SysUserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 认证相关接口（登录、退出、当前用户）。
 */
@Slf4j
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
     * 构造器注入
     *
     * @param userRepository 用户仓库
     * @param passwordEncoder 密码编码器
     */
    public AuthController(
            SysUserRepository userRepository,
            BCryptPasswordEncoder passwordEncoder
    ) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
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
     * 当前用户信息（不含 token）
     *
     * @param userId 用户 ID
     * @param username 用户名
     * @param realName 姓名
     * @param deptId 部门 ID
     * @param isAdmin 是否具备管理员角色
     */
    public record ProfileResponse(
            Long userId,
            String username,
            String realName,
            Long deptId,
            boolean isAdmin
    ) {}

    /**
     * 登录接口：校验用户名密码并创建 Sa-Token 会话
     *
     * @param req 登录请求
     * @return token
     */
    @PostMapping("/login")
    public Mono<Result<LoginResponse>> login(@Valid @RequestBody LoginRequest req) {
        return Mono.fromCallable(() -> userRepository.findByUsername(req.username()).orElse(null))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> {
                    if (user == null || !user.isEnabled()) {
                        log.warn("用户登录失败: username={}, reason={}", req.username(), "用户不存在或已禁用");
                        return Mono.error(new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误"));
                    }
                    if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
                        log.warn("用户登录失败: username={}, reason={}", req.username(), "密码错误");
                        return Mono.error(new BizException(ErrorCode.UNAUTHORIZED.getCode(), "用户名或密码错误"));
                    }
                    StpUtil.login(user.getId());
                    ProfileResponse profile = toProfile(user);
                    String token = StpUtil.getTokenValue();
                    log.info("用户登录成功: userId={}, username={}", user.getId(), user.getUsername());
                    return Mono.just(Results.success(new LoginResponse(
                            token,
                            profile.userId(),
                            profile.username(),
                            profile.realName(),
                            profile.deptId(),
                            profile.isAdmin()
                    )));
                });
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 用户资料
     */
    @GetMapping("/profile")
    public Mono<Result<ProfileResponse>> profile() {
        StpUtil.checkLogin();
        Long userId = Long.parseLong(StpUtil.getLoginIdAsString());
        return Mono.fromCallable(() -> {
                    SysUser user = userRepository.findById(userId).orElse(null);
                    if (user == null || !user.isEnabled()) {
                        throw new BizException(ErrorCode.UNAUTHORIZED);
                    }
                    return Results.success(toProfile(user));
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 退出接口：销毁当前 Sa-Token 会话
     *
     * @return 成功响应
     */
    @PostMapping("/logout")
    public Mono<Result<Void>> logout() {
        StpUtil.logout();
        return Mono.just(Results.success());
    }

    /**
     * 将用户实体映射为对外资料对象
     *
     * @param user 用户
     * @return 资料
     */
    private static ProfileResponse toProfile(SysUser user) {
        Long deptId = user.getDept() != null ? user.getDept().getId() : null;
        boolean isAdmin = user.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getCode()));
        return new ProfileResponse(
                user.getId(),
                user.getUsername(),
                user.getRealName(),
                deptId,
                isAdmin
        );
    }

}
