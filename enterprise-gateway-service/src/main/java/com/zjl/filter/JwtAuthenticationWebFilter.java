package com.zjl.filter;

import com.zjl.security.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.ReactiveAuthenticationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.server.authentication.AuthenticationWebFilter;
import org.springframework.security.web.server.authentication.ServerAuthenticationConverter;
import org.springframework.security.web.server.authentication.WebFilterChainServerAuthenticationSuccessHandler;
import org.springframework.security.web.server.authentication.ServerAuthenticationFailureHandler;
import reactor.core.publisher.Mono;

/**
 * 基于 JWT 的认证过滤器
 *
 * 作用：
 * 1. 从请求中提取 JWT token；
 * 2. 将 token 转换成 Spring Security 能识别的 Authentication 对象；
 * 3. 如果认证成功，则把认证信息放入 SecurityContext；
 * 4. 同时将用户信息写入 UserContext，方便后续业务代码获取当前登录用户；
 * 5. 如果认证失败，则交给 failureHandler 返回统一错误响应。
 *
 * 注意：
 * 实际的 token 解析、token 校验、用户身份构造逻辑，并不在这个类中完成，
 * 而是在传入的 ServerAuthenticationConverter 中完成。
 *
 * 这个类主要负责”组装认证过滤器流程”。
 */
@Slf4j
public class JwtAuthenticationWebFilter extends AuthenticationWebFilter {

    /**
     * 构造 JWT 认证过滤器
     *
     * AuthenticationWebFilter 是 Spring Security WebFlux 中用于认证的过滤器。
     * 当前类继承它，并对它的认证转换器、失败处理器、成功处理器进行自定义配置。
     *
     * @param converter      token -> Authentication 的转换器
     *                       负责从请求中提取 token，并完成 token 校验，
     *                       最后构造出 Authentication 对象。
     *
     * @param failureHandler 认证失败处理器
     *                       当 token 缺失、token 非法、token 过期等认证失败情况发生时，
     *                       由它统一返回 JSON 错误响应。
     */
    public JwtAuthenticationWebFilter(ServerAuthenticationConverter converter,
                                      ServerAuthenticationFailureHandler failureHandler) {

        /*
         * 调用父类 AuthenticationWebFilter 的构造方法。
         *
         * 这里传入的是 noopAuthenticationManager()，表示当前过滤器使用一个“透传式”的认证管理器
         */
        super(noopAuthenticationManager());

        /*
         * 设置认证转换器
         *
         * ServerAuthenticationConverter 的作用是：从 ServerWebExchange 中读取请求信息，例如请求头 Authorization，
         * 然后提取 JWT token，并转换成 Authentication。
         *
         * 简单理解：
         * HTTP Request -> JWT Token -> Authentication
         */
        setServerAuthenticationConverter(converter);

        /*
         * 设置认证失败处理器。
         *
         * 如果认证过程中出现异常，例如：
         * 1. token 不存在；
         * 2. token 格式错误；
         * 3. token 已过期；
         * 4. token 签名校验失败；
         * 5. converter 构造 Authentication 失败；
         *
         * 就会进入 failureHandler，由它返回统一格式的失败响应。
         */
        setAuthenticationFailureHandler(failureHandler);

        /*
         * 设置认证成功处理器。
         *
         * 当认证成功后，会执行这里的逻辑。
         *
         * 当前逻辑分为两步：
         * 1. setUserContext(authentication)
         *    把当前登录用户的信息写入 UserContext；
         *
         * 2. WebFilterChainServerAuthenticationSuccessHandler
         *    继续执行 Spring Security 默认的认证成功处理流程，
         *    让请求继续向后传递。
         */
        setAuthenticationSuccessHandler((webFilterExchange, authentication) -> {
            webFilterExchange.getExchange().getAttributes().put("AUTHENTICATION", authentication);
            String userId = String.valueOf(authentication.getPrincipal());
            String path = webFilterExchange.getExchange().getRequest().getPath().value();
            log.info("JWT认证通过: userId={}, path={}", userId, path);
            return setUserContext(authentication)
                    .then(new WebFilterChainServerAuthenticationSuccessHandler()
                            .onAuthenticationSuccess(webFilterExchange, authentication));
        });
    }

    /**
     * 无状态认证场景下的“透传式”认证管理器
     *
     * 在传统 Spring Security 中，AuthenticationManager 通常负责真正的用户名密码校验。
     * 但在 JWT 场景下，一般不会每次都查数据库重新认证，而是通过 token 自身完成身份校验。
     *
     * 所以这里的设计是：
     * 1. converter 已经完成 JWT 的解析和校验；
     * 2. converter 已经构造出 Authentication；
     * 3. 当前 AuthenticationManager 不再做二次校验；
     * 4. 只把 Authentication 标记为 authenticated = true；
     * 5. 然后直接返回。
     *
     * @return ReactiveAuthenticationManager 响应式认证管理器
     */
    private static ReactiveAuthenticationManager noopAuthenticationManager() {
        return authentication ->
                /*
                 * 直接透传。converter（RbacUtil）返回的 Authentication 已经是已认证状态
                 *（3 参构造函数内部已调用 super.setAuthenticated(true)），
                 * 此处不需要再次标记，否则会触发 IllegalArgumentException。
                 */
                reactor.core.publisher.Mono.just(authentication);
    }

    /**
     * 将认证信息写入 UserContext
     *
     * @param authentication Spring Security 中的认证信息
     * @return Mono<Void> 表示写入完成
     */
    private static Mono<Void> setUserContext(Authentication authentication) {
        try {
            /*
             * 用户 ID
             *
             * 这里先定义为 null，是为了兼容某些情况下 principal 不是数字的情况。
             * 如果 principal 可以成功转换成 Long，就作为 userId 使用。
             */
            Long userId = null;

            try {
                /*
                 * 从 authentication.getPrincipal() 中获取用户 ID。
                 */
                userId = Long.parseLong(String.valueOf(authentication.getPrincipal()));
            } catch (Exception ignored) {
                /*
                 * 如果 principal 不能转换成 Long，这里直接忽略异常。
                 *
                 * 例如 principal 是用户名、邮箱，或者为空，
                 * 那么 userId 就保持为 null。
                 */
            }

            /*
             * 从 authentication.getCredentials() 中获取用户名。
             *
             * 注意：
             * 在 Spring Security 传统语义中，credentials 通常表示凭证（密码、token 等）。
             * 但本项目中 credentials 存放的是 username（参见 RbacUtil.toAuthentication()），
             * 所以这里将 credentials 转成字符串后作为用户名使用。
             */
            String username = String.valueOf(authentication.getCredentials());

            /*
             * 获取当前用户的权限列表。
             *
             * authentication.getAuthorities() 返回的是 GrantedAuthority 集合。
             *
             * 每个 GrantedAuthority 代表一个权限或角色，例如：
             * ROLE_ADMIN
             * ROLE_USER
             * sys:user:list
             *
             * 这里通过 stream() 把权限对象转换成字符串列表。
             */
            var authorities = authentication.getAuthorities()
                    .stream()
                    .map(a -> a.getAuthority())
                    .toList();

            /*
             * 将用户信息写入 UserContext。
             *
             * 后续业务代码就可以从 UserContext 中获取当前登录用户信息，
             * 不需要每次都从 SecurityContext 或 Authentication 中解析。
             */
            UserContext.set(new UserContext.UserInfo(userId, username, authorities));

        } catch (Exception ignored) {
            /*
             * 防御性捕获异常。
             *
             * 即使写入 UserContext 失败，也不影响主认证流程继续执行。
             *
             * 但是实际项目中，如果这里失败比较重要，
             * 建议至少打印 debug 日志，方便后续排查。
             */
        }

        /*
         * 返回空 Mono。
         *
         * 表示当前方法没有实际响应数据，只表示 UserContext 写入动作已经结束。
         */
        return Mono.empty();
    }
}
