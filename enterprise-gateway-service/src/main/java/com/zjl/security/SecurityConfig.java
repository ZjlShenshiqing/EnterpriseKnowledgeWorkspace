package com.zjl.security;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.trace.TraceIdHolder;
import com.zjl.gateway.response.ApiResponseWriter;
import com.zjl.filter.JwtAuthenticationWebFilter;
import com.zjl.config.AppSecurityProperties;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * WebFlux 安全配置：默认鉴权、白名单放行、JWT 认证与统一错误响应。
 */
@Configuration
@EnableReactiveMethodSecurity
@EnableConfigurationProperties(AppSecurityProperties.class)
public class SecurityConfig {

    /**
     * Spring Security 过滤链
     *
     * @param http 安全配置构造器
     * @param props 安全配置项
     * @param jwtService JWT 服务
     * @param writer 统一 JSON 输出
     * @param tokenBlacklistService token 黑名单服务
     * @param rbacService RBAC 解析服务
     * @return 安全过滤链
     */
    @Bean
    public SecurityWebFilterChain securityWebFilterChain(
            ServerHttpSecurity http,
            AppSecurityProperties props,
            JwtUtil jwtService,
            ApiResponseWriter writer,
            TokenBlacklistService tokenBlacklistService,
            RbacUtil rbacService
    ) {
        List<String> whitelist = props.getWhitelist().getPaths();

        return http
                // 关闭 CSRF（前后端分离，无 Cookie-Session，无需 CSRF 防护）
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                // 使用默认 CORS 配置
                .cors(withDefaults())
                // 无状态模式，不依赖 Session 存储 SecurityContext
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                // 关闭表单登录 / HTTP Basic
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                // 请求结束后自动清理 UserContext，防止上下文泄漏
                .addFilterAfter((exchange, chain) ->
                        chain.filter(exchange).doFinally(signalType -> UserContext.clear()),
                        SecurityWebFiltersOrder.AUTHENTICATION
                )
                // URL 鉴权规则
                .authorizeExchange(exchanges ->
                        exchanges
                                // 白名单路径直接放行（登录、注册等）
                                .pathMatchers(whitelist.toArray(new String[0])).permitAll()
                                // 其余所有请求需要认证
                                .anyExchange().authenticated()
                )
                // 统一异常处理：401 未认证 / 403 无权限，返回 JSON
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((exchange, e) ->
                                writer.writeFailure(exchange, ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage(), traceId()))
                        .accessDeniedHandler((exchange, e) ->
                                writer.writeFailure(exchange, ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage(), traceId()))
                )
                // 在认证位置插入 JWT 过滤器，替换默认的表单认证
                .addFilterAt(new JwtAuthenticationWebFilter(
                                jwtAuthenticationConverter(jwtService, tokenBlacklistService, rbacService),
                                jwtFailureHandler(writer)
                        ),
                        SecurityWebFiltersOrder.AUTHENTICATION
                )
                .build();
    }

    /**
     * 从请求中提取 Bearer token 并转换为 Authentication。
     *
     * @param jwtService JWT 服务
     * @param tokenBlacklistService token 黑名单服务
     * @param rbacService RBAC 解析服务
     * @return converter
     */
    private ServerAuthenticationConverter jwtAuthenticationConverter(
            JwtUtil jwtService,
            TokenBlacklistService tokenBlacklistService,
            RbacUtil rbacService
    ) {
        return exchange -> {
            String authorization = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            // 无 Authorization 头或非 Bearer 格式，交给后续处理
            if (!StringUtils.hasText(authorization) || !authorization.startsWith("Bearer ")) {
                return Mono.empty();
            }
            String token = authorization.substring(7).trim();
            if (!isCompactJws(token)) {
                return Mono.error(new BadCredentialsException("无效的访问令牌"));
            }
            return tokenBlacklistService.isBlacklisted(token)
                    .flatMap(blacklisted -> {
                        if (Boolean.TRUE.equals(blacklisted)) {
                            return Mono.error(new BadCredentialsException("token revoked"));
                        }
                        return Mono.fromCallable(() -> jwtService.parse(token))
                                .subscribeOn(Schedulers.boundedElastic());
                    })
                    .flatMap(claims -> rbacService.toAuthentication(claims))
                    .map(authentication -> (Authentication) authentication)
                    .onErrorMap(ex -> ex instanceof BadCredentialsException
                            ? ex
                            : new BadCredentialsException("无效的访问令牌", ex));
        };
    }

    /**
     * 判断是否为紧凑 JWS（header.payload.signature，恰好两个 '.'）。
     */
    private static boolean isCompactJws(String token) {
        if (!StringUtils.hasText(token)) {
            return false;
        }
        int dots = 0;
        for (int i = 0; i < token.length(); i++) {
            if (token.charAt(i) == '.') {
                dots++;
            }
        }
        return dots == 2;
    }

    /**
     * JWT 认证失败时的统一响应。
     *
     * @param writer JSON 输出器
     * @return failure handler
     */
    private ServerAuthenticationFailureHandler jwtFailureHandler(ApiResponseWriter writer) {
        return (webFilterExchange, exception) ->
                writer.writeFailure(webFilterExchange.getExchange(), ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage(), traceId());
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

