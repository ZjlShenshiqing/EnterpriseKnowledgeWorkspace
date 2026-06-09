/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.common.request;

import lombok.Data;

/**
 * 业务标准分页请求对象
 *
 * @author zhangjlk
 * @date 2025/9/15 20:42
 */
@Data
public class PageRequest {

    /**
     * 当前页
     */
    private Long current = 1L;

    /**
     * 每页显示条数
     */
    private Long size = 10L;
}