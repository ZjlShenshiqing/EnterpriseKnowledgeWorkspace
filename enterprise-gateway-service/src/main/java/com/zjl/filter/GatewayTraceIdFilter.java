package com.zjl.filter;

import com.zjl.common.trace.TraceIdHolder;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 请求链路追踪过滤器
 *
 * 流程：
 * 1. 从请求头中读取 traceId；
 * 2. 如果请求头中没有 traceId，则自动生成一个新的 traceId；
 * 3. 将 traceId 放入 MDC，方便日志中统一打印；
 * 4. 将 traceId 写入响应头，方便前端或调用方排查问题；
 * 5. 请求结束后清理 MDC，避免线程复用导致 traceId 污染。
 */
@Component
public class GatewayTraceIdFilter implements WebFilter {

    /**
     * 请求头 / 响应头中用于传递 traceId 的字段名
     *
     * 例如：
     * X-Trace-Id: 8f3b7c7a-2e6d-4f7a-9b2e-xxxx
     */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    /**
     * 网关过滤器核心方法
     *
     * 每个请求进入网关时，都会经过该 filter 方法。
     *
     * @param exchange 当前请求与响应的上下文对象
     * @param chain    过滤器链，用于继续执行后续过滤器或转发请求
     * @return Mono<Void> WebFlux 异步执行结果
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {

        // 1. 从请求头中获取调用方传过来的 traceId
        // 如果是前端、上游服务、Nginx 或其他服务已经生成过 traceId，这里可以直接复用
        String incomingTraceId = exchange.getRequest()
                .getHeaders()
                .getFirst(TRACE_ID_HEADER);

        // 2. 判断请求头中的 traceId 是否有值
        // 如果上游已经传了 traceId，则继续使用这个 traceId；
        // 如果没有传，则通过 UUID 自动生成一个新的 traceId。
        String traceId = StringUtils.hasText(incomingTraceId)
                ? incomingTraceId
                : UUID.randomUUID().toString();

        // 3. 将 traceId 放入 MDC
        // MDC 是日志上下文容器，放进去之后，只要日志配置文件中配置了 traceId，打印日志时就可以自动带上当前请求的 traceId
        //
        // TraceIdHolder.key() 一般是统一返回 traceId 在 MDC 中使用的 key，
        // 例如可能返回 "traceId"。
        MDC.put(TraceIdHolder.key(), traceId);

        // 4. 将 traceId 写入响应头
        //
        // 这样调用方收到响应后，也能知道本次请求对应的 traceId。
        // 如果接口报错，前端或测试人员可以拿这个 traceId 去日志系统中搜索完整链路。
        exchange.getResponse()
                .getHeaders()
                .set(TRACE_ID_HEADER, traceId);

        // 5. 继续执行后续过滤器和业务逻辑
        //
        // chain.filter(exchange) 把请求继续往后传。
        //
        // doFinally(...) 会在请求处理结束时执行，
        // 不管请求是正常完成、异常结束，还是被取消，都会进入这里。
        return chain.filter(exchange)
                .doFinally(signalType -> {
                    // 6. 请求结束后清理 MDC 中的 traceId
                    //
                    // 这一步非常重要。
                    // 因为服务器线程可能会被复用，
                    // 如果不清理，可能导致下一个请求打印到上一个请求的 traceId。
                    MDC.remove(TraceIdHolder.key());
                });
    }
}

