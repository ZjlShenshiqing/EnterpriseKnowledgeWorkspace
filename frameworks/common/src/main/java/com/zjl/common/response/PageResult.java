package com.zjl.common.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 通用分页响应结构，与项目 API 约定一致。
 *
 * @param <T> 记录类型
 */
@Getter
@Builder
public class PageResult<T> {

    /**
     * 当前页码
     */
    private final long current;

    /**
     * 每页条数
     */
    private final long size;

    /**
     * 总条数
     */
    private final long total;

    /**
     * 当前页记录列表
     */
    private final List<T> records;

    /**
     * 快速构建分页结果。
     *
     * @param current 当前页码
     * @param size    每页条数
     * @param total   总条数
     * @param records 当前页记录
     * @param <T>     记录类型
     * @return 分页响应
     */
    public static <T> PageResult<T> of(long current, long size, long total, List<T> records) {
        return PageResult.<T>builder()
                .current(current)
                .size(size)
                .total(total)
                .records(records)
                .build();
    }
}
