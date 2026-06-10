/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.designpattern.chain;

import org.openzjl.index12306.framework.starter.bases.ApplicationContextHolder;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 抽象责任链上下文
 *
 * 这个类是一个 “责任链调度中心”，它的作用是：
 *
 * 启动时自动从 Spring 容器中找出所有实现了 AbstractChainHandler 的处理器；
 * 按照它们的 mark() 分类；
 * 在每个分类里按 order 排序；
 * 提供一个统一入口：handler(mark, requestParam)，让你可以“一键触发某条责任链”。
 *
 * @author zhangjlk
 * @date 2025/9/17 19:43
 */
// CommandLineRunner 是 Spring Boot 提供的一个接口，它的作用是：在项目启动完成后，自动执行一段代码
public final class AbstractChainContext<T> implements CommandLineRunner {

    // 自己定义的那些实现了 AbstractChainHandler 接口的 Bean 的集合
    private final Map<String, List<AbstractChainHandler>> abstractChainHandlerContainer = new HashMap<>();

    /**
     * 责任链执行
     * @param mark          责任链组件标识
     * @param requestParam  请求参数
     */
    public void handler(String mark, T requestParam) {
        List<AbstractChainHandler> abstractChainHandlers = abstractChainHandlerContainer.get(mark);
        if (CollectionUtils.isEmpty(abstractChainHandlers)) {
            throw new RuntimeException("No chain handler for mark: " + mark);
        }
        // 依次执行责任链里面的handler
        abstractChainHandlers.forEach(handler -> handler.handler(requestParam));
    }

    /**
     * Spring 启动完成后执行：从容器中收集所有责任链 Handler，按 mark 分组、组内按 order 排序后放入 abstractChainHandlerContainer，
     * 供后续 handler(mark, requestParam) 按链执行。
     */
    @Override
    public void run(String... args) throws Exception {
        // 从 Spring 容器中取出所有实现了 AbstractChainHandler 的 Bean（如用户注册 3 个、购票/退款等各链的 Handler）
        Map<String, AbstractChainHandler> chainFilterMap = ApplicationContextHolder
                .getBeansOfType(AbstractChainHandler.class);

        chainFilterMap.forEach((beanName, bean) -> {
            // 用当前 Bean 的 mark() 作为“链标识”，取出该链已有的 Handler 列表（可能为空）
            List<AbstractChainHandler> abstractChainHandlers = abstractChainHandlerContainer.get(bean.mark());
            if (CollectionUtils.isEmpty(abstractChainHandlers)) {
                abstractChainHandlers = new ArrayList<>();
            }
            // 把当前 Bean 加入这条链
            abstractChainHandlers.add(bean);
            // 按 getOrder() 升序排序，保证链内执行顺序（0 → 1 → 2 …）
            List<AbstractChainHandler> actualAbstractChanHandlers = abstractChainHandlers.stream()
                    .sorted(Comparator.comparing(Ordered::getOrder))
                    .collect(Collectors.toList());
            // 将排好序的链写回 Map：key = mark，value = 该链的 Handler 有序列表
            abstractChainHandlerContainer.put(bean.mark(), actualAbstractChanHandlers);
        });
    }
}
