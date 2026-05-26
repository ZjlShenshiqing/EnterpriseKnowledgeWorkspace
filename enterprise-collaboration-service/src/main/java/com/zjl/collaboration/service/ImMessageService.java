package com.zjl.collaboration.service;

import java.util.Map;

/**
 * IM 消息服务
 */
public interface ImMessageService {

    Map<String, Object> send(Long senderId, String senderName, Long conversationId,
                             String content, String clientMsgId);
}
