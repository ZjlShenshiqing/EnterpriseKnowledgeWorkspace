package com.zjl.collaboration.service;

import java.util.List;
import java.util.Map;

/**
 * 文档评论业务服务。
 */
public interface DocCommentService {

    /**
     * 查询文档评论。
     *
     * @param docId 文档 ID
     * @return 评论树
     */
    List<Map<String, Object>> list(Long docId);

    /**
     * 创建评论。
     *
     * @param docId        文档 ID
     * @param content      内容
     * @param anchorIndex  锚点位置
     * @param anchorLength 锚点长度
     * @param parentId     父评论 ID
     * @param userId       用户 ID
     * @return 评论视图
     */
    Map<String, Object> create(Long docId, String content, Integer anchorIndex, Integer anchorLength,
                               Long parentId, Long userId);

    /**
     * 更新评论。
     *
     * @param id       评论 ID
     * @param content  内容
     * @param resolved 是否解决
     * @return 是否找到评论
     */
    boolean update(Long id, String content, Integer resolved);

    /**
     * 删除评论。
     *
     * @param id 评论 ID
     */
    void delete(Long id);
}
