/*
 * Copyright (c) 2025-2026 zhangjlk
 * All rights reserved.
 */
package com.zjl.framework.starter.distributedid.core;

/**
 * 全局唯一 ID 生成器接口。
 * <p>
 * 定义统一的 ID 生成规范，便于在不同实现（如雪花算法、号段模式、数据库自增等）之间进行替换。
 * 默认实现返回占位值，实际项目应提供具体实现类并覆盖默认方法。
 * </p>
 *
 * @author zhangjlk
 * @date 2025/9/24 16:13
 */
public interface IdGenerator {

    /**
     * 生成下一个全局唯一的长整型 ID。
     * <p>
     * 默认实现返回占位值 {@code 0L}，请在具体实现中覆盖。
     * </p>
     *
     * @return 全局唯一 ID（long）
     */
    default long nextId() {
        return 0L;
    }

    /**
     * 生成下一个全局唯一的字符串 ID。
     * <p>
     * 默认实现返回空字符串，请在具体实现中覆盖。
     * </p>
     *
     * @return 全局唯一 ID（字符串）
     */
    default String nextIdStr() {
        return "";
    }
}
