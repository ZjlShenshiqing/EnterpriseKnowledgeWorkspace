package com.zjl.collaboration.service;

import com.zjl.collaboration.entity.ImMessage;

import java.util.List;
import java.util.Map;

/**
 * 即时通讯业务服务。
 */
public interface ChatService {

    /**
     * 查询会话列表。
     *
     * @param userId 用户 ID
     * @return 会话列表
     */
    List<Map<String, Object>> conversations(Long userId);

    /**
     * 查询未读总数。
     *
     * @param userId 用户 ID
     * @return 未读数
     */
    int unreadCount(Long userId);

    /**
     * 查询消息。
     *
     * @param convId 会话 ID
     * @param page   页码
     * @param size   每页大小
     * @return 消息列表
     */
    List<ImMessage> messages(Long convId, int page, int size);

    /**
     * 创建会话。
     *
     * @param name      名称
     * @param type      类型
     * @param memberIds 成员 ID
     * @param userId    创建人
     * @return 会话 ID
     */
    Long createConversation(String name, String type, List<Long> memberIds, Long userId);

    /**
     * 查询成员。
     *
     * @param convId 会话 ID
     * @return 成员列表
     */
    List<Map<String, Object>> members(Long convId);
}
