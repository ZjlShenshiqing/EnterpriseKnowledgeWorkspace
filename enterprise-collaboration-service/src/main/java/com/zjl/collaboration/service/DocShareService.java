package com.zjl.collaboration.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 文档分享业务服务。
 */
public interface DocShareService {

    /**
     * 查询协作者。
     *
     * @param docId 文档 ID
     * @return 协作者列表
     */
    List<Map<String, Object>> listCollaborators(Long docId);

    /**
     * 添加协作者。
     *
     * @param docId      文档 ID
     * @param targetType 目标类型
     * @param targetId   目标 ID
     * @param permission 权限
     * @return 创建结果
     */
    Map<String, Object> addCollaborator(Long docId, String targetType, Long targetId, String permission);

    /**
     * 更新协作者。
     *
     * @param id         协作者 ID
     * @param permission 权限
     */
    void updateCollaborator(Long id, String permission);

    /**
     * 移除协作者。
     *
     * @param id 协作者 ID
     */
    void removeCollaborator(Long id);

    /**
     * 查询分享链接。
     *
     * @param docId 文档 ID
     * @return 分享链接列表
     */
    List<Map<String, Object>> listShares(Long docId);

    /**
     * 创建分享链接。
     *
     * @param docId      文档 ID
     * @param permission 权限
     * @param expiredAt  过期时间
     * @return 创建结果
     */
    Map<String, Object> createShare(Long docId, String permission, LocalDateTime expiredAt);

    /**
     * 删除分享链接。
     *
     * @param id 分享链接 ID
     */
    void deleteShare(Long id);

    /**
     * 通过分享 token 打开文档。
     *
     * @param token 分享 token
     * @return 文档详情
     */
    Map<String, Object> openByToken(String token);
}
