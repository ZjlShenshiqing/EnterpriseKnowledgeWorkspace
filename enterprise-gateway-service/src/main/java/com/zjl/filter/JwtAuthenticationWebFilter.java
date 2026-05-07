package com.zjl.filter;

import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import org.springframework.security.web.server.authentication.WebFilterChainServerAuthenticationSuccessHandler;
import reactor.core.publisher.Mono;

/**
 * 基于 JWT 的认证过滤器：从请求中提取 token -> 转为 Authentication -> 放入 SecurityContext
 *
 * <p>注意：实际 token 校验在 converter 中完成；此类只做过滤器组装</p>
 */
public class JwtAuthenticationWebFilter extends AuthenticationWebFilter {

    /**
     * 构造 JWT 认证过滤器
     *
     * @param converter token -> Authentication 的转换器（负责校验与构造已认证对象）
     * @param failureHandler 认证失败处理器（统一返回结构）
     */
    public JwtAuthenticationWebFilter(ServerAuthenticationConverter converter, ServerAuthenticationFailureHandler failureHandler) {
        super(noopAuthenticationManager());
        setServerAuthenticationConverter(converter);
        setAuthenticationFailureHandler(failureHandler);
        setAuthenticationSuccessHandler((webFilterExchange, authentication) ->
                setUserContext(authentication)
                        .then(new WebFilterChainServerAuthenticationSuccessHandler().onAuthenticationSuccess(webFilterExchange, authentication)));
    }

    /**
     * 无状态认证场景下的“透传式”认证管理器
     *
     * <p>约定：converter 已经产出“已认证”的 Authentication；这里不做二次校验，仅将其标记为已认证并放行</p>
     *
     * @return ReactiveAuthenticationManager
     */
    private static ReactiveAuthenticationManager noopAuthenticationManager() {
        return authentication -> {
            authentication.setAuthenticated(true);
            return reactor.core.publisher.Mono.just(authentication);
        };
    }

    /**
     * 将认证信息写入 UserContext 并注册清理逻辑
     *
     * @param authentication 认证信息
     * @return 完成信号
     */
    private static Mono<Void> setUserContext(Authentication authentication) {
        try {
            Long userId = null;
            try {
                userId = Long.parseLong(String.valueOf(authentication.getPrincipal()));
            } catch (Exception ignored) {
            }
            String username = String.valueOf(authentication.getCredentials());
            var authorities = authentication.getAuthorities().stream().map(a -> a.getAuthority()).toList();
            UserContext.set(new UserContext.UserInfo(userId, username, authorities));
        } catch (Exception ignored) {
        }
        return Mono.empty();
    }
}

