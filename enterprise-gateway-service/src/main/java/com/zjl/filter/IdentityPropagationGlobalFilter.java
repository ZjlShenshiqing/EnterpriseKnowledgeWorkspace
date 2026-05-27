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
        if (!StpUtil.isLogin()) {
            return chain.filter(exchange);
        }
        String userId = StpUtil.getLoginIdAsString();
        return Mono.fromCallable(() -> userRepository.findById(Long.parseLong(userId)).orElse(null))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(user -> chain.filter(mutateRequest(exchange, userId, user)))
                .switchIfEmpty(chain.filter(mutateRequest(exchange, userId, null)));
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
