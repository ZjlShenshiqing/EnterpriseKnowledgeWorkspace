package com.zjl.collaboration.service;

import com.zjl.common.response.PageResult;

import java.util.Map;

/**
 * 在线文档业务服务。
 */
public interface DocService {

    /**
     * 分页查询文档。
     *
     * @param keyword 关键词
     * @param page    页码
     * @param size    每页大小
     * @return 文档分页
     */
    PageResult<Map<String, Object>> list(String keyword, int page, int size);

    /**
     * 获取文档详情。
     *
     * @param id 文档 ID
     * @return 文档详情
     */
    Map<String, Object> get(Long id);

    /**
     * 创建文档。
     *
     * @param title  标题
     * @param userId 创建人
     * @return 创建结果
     */
    Map<String, Object> create(String title, Long userId);

    /**
     * 更新文档。
     *
     * @param id    文档 ID
     * @param title 标题
     */
    void update(Long id, String title);

    /**
     * 删除文档。
     *
     * @param id 文档 ID
     */
    void delete(Long id);
}
