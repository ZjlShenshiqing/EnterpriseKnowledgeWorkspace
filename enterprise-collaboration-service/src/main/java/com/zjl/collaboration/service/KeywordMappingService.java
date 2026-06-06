package com.zjl.collaboration.service;

import com.zjl.collaboration.entity.KbKeywordMapping;
import com.zjl.common.response.PageResult;

import java.util.Map;

/**
 * 关键词映射业务服务。
 */
public interface KeywordMappingService {

    /**
     * 分页查询。
     *
     * @param current 当前页
     * @param size    每页大小
     * @param keyword 关键词
     * @return 分页结果
     */
    PageResult<KbKeywordMapping> list(int current, int size, String keyword);

    /**
     * 创建映射。
     *
     * @param mapping 映射
     * @return 创建后的映射
     */
    KbKeywordMapping create(KbKeywordMapping mapping);

    /**
     * 更新映射。
     *
     * @param id      ID
     * @param mapping 映射
     * @return 更新后的映射
     */
    KbKeywordMapping update(Long id, KbKeywordMapping mapping);

    /**
     * 删除映射。
     *
     * @param id ID
     */
    void delete(Long id);

    /**
     * 匹配查询文本。
     *
     * @param query 查询文本
     * @return 匹配结果
     */
    Map<String, Object> match(String query);
}
