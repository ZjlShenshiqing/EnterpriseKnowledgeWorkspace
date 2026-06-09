/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.common.toolkit;

import lombok.SneakyThrows;

/**
 * 线程工具类
 *
 * @author zhangjlk
 * @date 2025/9/18 10:38
 */
public class ThreadUtil {

    /**
     * 睡眠当前线程指定时间
     *
     * @param millis 睡眠时间，单位毫秒
     */
    @SneakyThrows(value = InterruptedException.class)
    public static void sleep(long millis) {
        Thread.sleep(millis);
    }
}