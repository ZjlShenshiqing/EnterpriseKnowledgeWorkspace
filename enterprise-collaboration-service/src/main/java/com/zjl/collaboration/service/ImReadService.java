package com.zjl.collaboration.service;

/**
 * IM 已读状态服务
 */
public interface ImReadService {

    void markRead(Long userId, Long conversationId, Long lastReadMsgId);

    int unreadCount(Long userId, Long conversationId);
}
