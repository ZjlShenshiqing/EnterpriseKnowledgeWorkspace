package com.zjl.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 将网关已认证的用户身份写入下游请求头，并移除 Authorization，避免下游重复校验 JWT。
 */
@Slf4j
@Component
public class IdentityPropagationGlobalFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        return resolveAuthentication(exchange)
                .flatMap(auth -> chain.filter(mutateRequest(exchange, auth)))
                .switchIfEmpty(chain.filter(exchange));
    }

    private Mono<Authentication> resolveAuthentication(ServerWebExchange exchange) {
        Authentication attributeAuth = exchange.getAttribute("AUTHENTICATION");
        if (attributeAuth != null && attributeAuth.isAuthenticated()) {
            return Mono.just(attributeAuth);
        }
        return ReactiveSecurityContextHolder.getContext()
                .map(ctx -> ctx.getAuthentication())
                .filter(auth -> auth != null && auth.isAuthenticated());
    }

    private ServerWebExchange mutateRequest(ServerWebExchange exchange, Authentication auth) {
        String userId = String.valueOf(auth.getPrincipal());
        String targetUri = exchange.getRequest().getURI().toString();
        log.debug("身份头注入: userId={}, downstream={}", userId, targetUri);
        boolean admin = auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equalsIgnoreCase(a.getAuthority()));
        String deptId = exchange.getRequest().getHeaders().getFirst("X-Department-Id");
        var builder = exchange.getRequest().mutate()
                .header("X-User-Id", userId)
                .header("X-Is-Admin", admin ? "true" : "false")
                .headers(headers -> headers.remove(HttpHeaders.AUTHORIZATION));
        if (deptId != null && !deptId.isBlank()) {
            builder.header("X-Department-Id", deptId);
        }
        return exchange.mutate().request(builder.build()).build();
    }

    @Override
    public int getOrder() {
        // 必须大于 SecurityWebFiltersOrder.AUTHENTICATION (100)，确保在认证完成后执行
        return 200;
    }
}
