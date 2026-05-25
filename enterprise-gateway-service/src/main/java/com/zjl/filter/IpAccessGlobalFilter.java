package com.zjl.filter;

import com.zjl.gateway.response.ApiResponseWriter;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.trace.TraceIdHolder;
import com.zjl.config.AppGatewayProperties;
import lombok.extern.slf4j.Slf4j;
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
 * IP 访问控制全局过滤器（黑名单 / 白名单）
 *
 * 流程：
 * 1. 在请求进入网关后，先获取客户端 IP；
 * 2. 读取配置文件中的 IP 白名单和黑名单；
 * 3. 如果配置了白名单，则只有白名单中的 IP 才允许访问；
 * 4. 如果配置了黑名单，则黑名单中的 IP 禁止访问；
 * 5. 如果校验不通过，则直接返回统一 JSON 错误响应；
 * 6. 如果校验通过，则继续执行后续网关过滤器和业务请求。
 */
@Slf4j
@Component
@EnableConfigurationProperties(AppGatewayProperties.class)
public class IpAccessGlobalFilter implements GlobalFilter, Ordered {

    /**
     * 网关配置项
     *
     * 用于读取 application.yml / application.properties 中配置的网关参数，
     * 例如 IP 白名单、IP 黑名单等。
     */
    private final AppGatewayProperties props;

    /**
     * 统一 JSON 输出器
     *
     * 当请求被拦截时，用它返回统一格式的错误响应，
     * 避免直接返回杂乱的错误信息。
     */
    private final ApiResponseWriter writer;

    /**
     * 构造器注入
     *
     * Spring 会自动把 AppGatewayProperties 和 ApiResponseWriter 注入进来。
     *
     * @param props  网关配置项，包含 IP 白名单和黑名单配置
     * @param writer 统一 JSON 响应输出工具
     */
    public IpAccessGlobalFilter(AppGatewayProperties props, ApiResponseWriter writer) {
        this.props = props;
        this.writer = writer;
    }

    /**
     * 全局过滤器核心方法
     *
     * 每个请求经过 Spring Cloud Gateway 时，都会进入该方法
     * 这里主要负责对请求来源 IP 做访问控制
     *
     * @param exchange 当前请求和响应的上下文对象
     * @param chain    网关过滤器链，用于继续执行后续过滤器
     * @return Mono<Void> WebFlux 异步返回结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
                             org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {

        // 1. 获取当前请求的客户端 IP
        // 这里是基于 request.getRemoteAddress() 获取的 IP。
        String ip = ip(exchange);

        // 2. 从配置项中获取 IP 白名单
        // 如果 whitelist 不为空，则表示系统开启了白名单限制。
        List<String> whitelist = props.getIp().getWhitelist();

        // 3. 从配置项中获取 IP 黑名单
        // 如果 blacklist 不为空，则表示系统开启了黑名单限制。
        List<String> blacklist = props.getIp().getBlacklist();

        // 4. 白名单校验
        //
        // 逻辑说明：
        // 如果配置了白名单，那么只有白名单里的 IP 才能访问。
        //
        // 判断条件拆解：
        // !CollectionUtils.isEmpty(whitelist)
        // 表示白名单不为空，即启用了白名单机制。
        //
        // ip == null
        // 表示没有获取到客户端 IP，这种情况下也不允许访问。
        //
        // !whitelist.contains(ip)
        // 表示当前 IP 不在白名单中，也不允许访问。
        if (!CollectionUtils.isEmpty(whitelist) && (ip == null || !whitelist.contains(ip))) {
            String path = exchange.getRequest().getPath().value();
            log.warn("IP白名单拦截: IP={}, path={}", ip, path);

            // 如果 IP 不在白名单内，直接返回 403 禁止访问
            // traceId() 用于把当前请求的链路追踪 ID 一起返回，方便排查日志。
            return writer.writeFailure(
                    exchange,
                    ErrorCode.FORBIDDEN.getCode(),
                    "IP 未在白名单内",
                    traceId()
            );
        }

        // 5. 黑名单校验
        //
        // 逻辑说明：
        // 如果配置了黑名单，并且当前 IP 在黑名单中，则禁止访问。
        //
        // 注意：
        // 黑名单是在白名单之后校验的。
        // 也就是说，如果白名单开启了，请求必须先通过白名单校验。
        if (!CollectionUtils.isEmpty(blacklist) && ip != null && blacklist.contains(ip)) {
            String path = exchange.getRequest().getPath().value();
            log.warn("IP黑名单拦截: IP={}, path={}", ip, path);

            // 如果当前 IP 在黑名单中，直接返回 403 禁止访问
            return writer.writeFailure(
                    exchange,
                    ErrorCode.FORBIDDEN.getCode(),
                    "IP 已被禁止访问",
                    traceId()
            );
        }

        // 6. 如果白名单和黑名单都校验通过，则继续执行后续过滤器
        //
        // chain.filter(exchange) 表示放行当前请求，请求会继续向后进入鉴权、限流、路由转发等后续流程
        return chain.filter(exchange);
    }

    /**
     * 设置过滤器执行顺序
     *
     * 数值越小，优先级越高，越早执行。
     *
     * 当前返回 -100，说明该过滤器会优先于大多数业务过滤器执行，
     * 这样可以在请求进入后续业务逻辑之前，先完成 IP 访问控制。
     *
     * @return 过滤器执行顺序
     */
    @Override
    public int getOrder() {
        return -100;
    }

    /**
     * 获取客户端 IP
     *
     * 当前实现是从 remoteAddress 中获取 IP。
     *
     * remoteAddress 表示和网关建立连接的远程地址。
     * 在没有经过 Nginx、负载均衡、反向代理的情况下，
     * 这里通常可以拿到真实客户端 IP。
     *
     * 但是如果请求经过 Nginx、SLB、Ingress 等代理，
     * remoteAddress 获取到的可能是代理服务器的 IP，
     * 不是用户真实 IP。
     *
     * @param exchange 当前请求上下文
     * @return 客户端 IP；如果无法获取，则返回 null
     */
    private static String ip(ServerWebExchange exchange) {

        // 获取请求的远程地址信息
        InetSocketAddress addr = exchange.getRequest().getRemoteAddress();

        // 如果远程地址为空，或者地址中的 IP 信息为空，则说明无法获取客户端 IP
        if (addr == null || addr.getAddress() == null) {
            return null;
        }

        // 返回 IP 地址字符串
        // 例如：127.0.0.1、192.168.1.10、0:0:0:0:0:0:0:1
        return addr.getAddress().getHostAddress();
    }

    /**
     * 从 MDC 中获取当前请求的 traceId
     *
     * traceId 一般由前面的 traceId 过滤器生成或透传，
     * 并提前放入 MDC 中。
     *
     * 当这里返回错误响应时，把 traceId 一起返回给调用方，
     * 调用方就可以拿着这个 traceId 去日志系统中搜索完整请求链路。
     *
     * @return 当前请求的 traceId；如果 MDC 中不存在，则返回 null
     */
    private String traceId() {
        return MDC.get(TraceIdHolder.key());
    }
}

