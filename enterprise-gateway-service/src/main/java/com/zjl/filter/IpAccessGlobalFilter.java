package com.zjl.filter;

import com.zjl.common.response.ApiResponseWriter;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.trace.TraceIdHolder;
import com.zjl.config.AppGatewayProperties;
import org.slf4j.MDC;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.util.List;

/**
 * IP 访问控制全局过滤器（黑名单/白名单）
 */
@Component
@EnableConfigurationProperties(AppGatewayProperties.class)
public class IpAccessGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 网关配置项
     */
    private final AppGatewayProperties props;
    /**
     * 统一 JSON 输出器
     */
    private final ApiResponseWriter writer;

    /**
     * 构造器注入
     *
     * @param props 网关配置项
     * @param writer JSON 输出器
     */
    public IpAccessGlobalFilter(AppGatewayProperties props, ApiResponseWriter writer) {
        this.props = props;
        this.writer = writer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        String ip = ip(exchange);
        List<String> whitelist = props.getIp().getWhitelist();
        List<String> blacklist = props.getIp().getBlacklist();

        if (!CollectionUtils.isEmpty(whitelist) && (ip == null || !whitelist.contains(ip))) {
            return writer.writeFailure(exchange, ErrorCode.FORBIDDEN.getCode(), "IP 未在白名单内", traceId());
        }
        if (!CollectionUtils.isEmpty(blacklist) && ip != null && blacklist.contains(ip)) {
            return writer.writeFailure(exchange, ErrorCode.FORBIDDEN.getCode(), "IP 已被禁止访问", traceId());
        }
        return chain.filter(exchange);
    }

    /**
     * 过滤器顺序：优先于多数业务过滤器
     *
     * @return order
     */
    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * 获取客户端 IP（基于 remoteAddress）
     *
     * @param exchange exchange
     * @return ip
     */
    private static String ip(ServerWebExchange exchange) {
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();
        if (addr == null || addr.getAddress() == null) {
            return null;
        }
        return addr.getAddress().getHostAddress();
    }

    /**
     * 从 MDC 获取 traceId
     *
     * @return traceId
     */
    private String traceId() {
        return MDC.get(TraceIdHolder.key());
    }
}

