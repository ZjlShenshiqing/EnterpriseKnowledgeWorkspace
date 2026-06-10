/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.designpattern.chain;

import org.springframework.core.Ordered;

/**
 * 抽象业务责任链
 * @author zhangjlk
 * @date 2025/9/17 19:43
 */
// Ordered 控制责任链执行顺序先后
public interface AbstractChainHandler<T> extends Ordered {

    /**
     * 执行责任链逻辑
     *
     * @param requestParam 责任链执行入参
     */
    void handler(T requestParam);

    /**
     * @return 责任链执行标识
     */
    String mark();
}
