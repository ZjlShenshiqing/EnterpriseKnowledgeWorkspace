package com.zjl.filter;

import cn.dev33.satoken.stp.StpUtil;
import com.zjl.domain.SysUser;
import com.zjl.repository.SysUserRepository;
import com.zjl.security.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

/**
 * 将网关已认证的用户身份写入下游请求头，并移除 Authorization，避免下游重复校验 Token。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IdentityPropagationGlobalFilter implements GlobalFilter, Ordered {

    private final SysUserRepository userRepository;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        // 注意：
        // GatewayFilter 的执行链和 SaReactorFilter 可能不在同一个线程上下文中。
        //
        // 所以这里不要直接调用 StpUtil.isLogin() / StpUtil.getLoginId()，
        // 否则可能因为 Sa-Token 上下文丢失，导致拿不到登录信息。
        //
        // 这里选择直接从请求头中解析 token，
        // 再通过 StpUtil.getLoginIdByToken(token) 反查 loginId。
        String token = resolveToken(exchange);

        // 请求中没有 token，说明当前请求无法识别用户身份。
        // 这里不拦截请求，直接交给后续过滤器继续处理。
        if (token == null) {
            return chain.filter(exchange);
        }

        // 根据 token 反查登录用户 ID。
        //
        // getLoginIdByToken(token) 不依赖当前线程上下文，
        // 适合在 GatewayFilter 这种响应式过滤器中使用。
        Object loginId = StpUtil.getLoginIdByToken(token);

        // token 无效、过期，或者找不到对应登录信息时，直接放行原请求。
        // 后续真正需要登录的接口，会由鉴权逻辑继续拦截。
        if (loginId == null) {
            return chain.filter(exchange);
        }

        // Sa-Token 中保存的是 Object 类型 loginId，
        // 这里统一转换成字符串形式的 userId，方便后续写入请求头。
        String userId = loginId.toString();

        // 查询用户信息是阻塞操作，不能直接放在 Netty 事件循环线程里执行。
        //
        // 所以这里使用 Mono.fromCallable(...) 包一层，
        // 再通过 subscribeOn(Schedulers.boundedElastic())，
        // 把数据库查询切换到适合处理阻塞任务的线程池中。
        return Mono.fromCallable(() -> userRepository.findById(Long.parseLong(userId)).orElse(null))
                .subscribeOn(Schedulers.boundedElastic())

                // 如果查到了用户，就把 userId 和用户信息写入请求头，
                // 然后继续执行后续网关过滤器链。
                .flatMap(user -> chain.filter(mutateRequest(exchange, userId, user)))

                // 如果数据库中查不到用户，Mono.fromCallable 会变成空结果。
                // 此时仍然把 userId 写入请求头，但用户详细信息传 null。
                .switchIfEmpty(chain.filter(mutateRequest(exchange, userId, null)));
    }

    /**
     * 从请求头读取 Sa-Token（兼容 Bearer 前缀）。
     *
     * @param exchange 当前请求
     * @return token 或 null
     */
    private static String resolveToken(ServerWebExchange exchange) {
        String raw = exchange.getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        raw = raw.trim();
        if (raw.startsWith("Bearer ")) {
            raw = raw.substring(7).trim();
        }
        return raw.isEmpty() ? null : raw;
    }

    private ServerWebExchange mutateRequest(ServerWebExchange exchange, String userId, SysUser user) {
        boolean admin = user != null && user.getRoles().stream()
                .anyMatch(r -> "admin".equalsIgnoreCase(r.getCode()));
        if (user != null) {
            UserContext.set(new UserContext.UserInfo(
                    user.getId(),
                    user.getUsername(),
                    authoritiesOf(user)
            ));
        }
        String targetUri = exchange.getRequest().getURI().toString();
        log.debug("身份头注入: userId={}, downstream={}", userId, targetUri);
        var builder = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-Is-Admin", admin ? "true" : "false")
                .headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION));
        if (user != null && user.getDept() != null) {
            builder.header("X-Department-Id", String.valueOf(user.getDept().getId()));
        }
        return exchange.mutate().request(builder.build()).build();
    }

    private static List<String> authoritiesOf(SysUser user) {
        List<String> result = new ArrayList<>();
        user.getRoles().forEach(role -> {
            result.add("ROLE_" + role.getCode().toUpperCase());
            role.getPermissions().forEach(p -> result.add(p.getCode()));
        });
        return result;
    }

    @Override
    public int getOrder() {
        return 200;
    }
}
