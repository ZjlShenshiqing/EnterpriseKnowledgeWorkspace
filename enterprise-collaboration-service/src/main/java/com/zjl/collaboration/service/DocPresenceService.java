package com.zjl.collaboration.service;

import org.springframework.web.socket.WebSocketSession;
import java.util.Map;
import java.util.Set;

/**
 * 文档在线状态服务
 */
public interface DocPresenceService {

    void join(Long docId, String sessionId, Long userId, String userName, WebSocketSession session);

    void leave(Long docId, String sessionId);

    Map<String, SessionInfo> getSubscribers(Long docId);

    int getOnlineCount(Long docId);

    void trackSubscription(String sessionId, Long docId);

    Set<Long> removeSession(String sessionId);

    record SessionInfo(Long userId, String userName, WebSocketSession session) {}
}
