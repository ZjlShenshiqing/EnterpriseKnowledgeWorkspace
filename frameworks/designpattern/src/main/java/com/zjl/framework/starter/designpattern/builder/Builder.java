/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.designpattern.builder;

import java.io.Serializable;

/**
 * Builder模式抽象接口
 * @author zhangjlk
 * @date 2025/9/17 19:42
 */
public interface Builder<T> extends Serializable {

    /**
     * 构建方法
     *
     * @return 构建后的对象
     */
    T build();
}
