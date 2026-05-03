package com.zjl.security;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.trace.TraceIdHolder;
import com.zjl.common.response.ApiResponseWriter;
import com.zjl.filter.JwtAuthenticationWebFilter;
import com.zjl.config.AppSecurityProperties;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.config.web.server.SecurityWebFiltersOrder;
import org.springframework.security.web.server.context.NoOpServerSecurityContextRepository;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

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
     * Spring Security 过滤链。
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
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(withDefaults())
                .securityContextRepository(NoOpServerSecurityContextRepository.getInstance())
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable)
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable)
                .addFilterAfter((exchange, chain) ->
                        chain.filter(exchange).doFinally(signalType -> UserContext.clear()),
                        SecurityWebFiltersOrder.AUTHENTICATION
                )
                .authorizeExchange(exchanges ->
                        exchanges
                                .pathMatchers(whitelist.toArray(new String[0])).permitAll()
                                .anyExchange().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((exchange, e) ->
                                writer.writeFailure(exchange, ErrorCode.UNAUTHORIZED.getCode(), ErrorCode.UNAUTHORIZED.getMessage(), traceId()))
                        .accessDeniedHandler((exchange, e) ->
                                writer.writeFailure(exchange, ErrorCode.FORBIDDEN.getCode(), ErrorCode.FORBIDDEN.getMessage(), traceId()))
                )
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
            String auth = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
            if (!StringUtils.hasText(auth) || !auth.startsWith("Bearer ")) {
                return Mono.empty();
            }
            String token = auth.substring(7);
            return tokenBlacklistService.isBlacklisted(token)
                    .flatMap(blacklisted -> {
                        if (blacklisted) {
                            return Mono.error(new RuntimeException("token revoked"));
                        }
                        return Mono.fromCallable(() -> jwtService.parse(token))
                                .flatMap(claims -> rbacService.toAuthentication(claims).cast(AbstractAuthenticationToken.class));
                    });
        };
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

