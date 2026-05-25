package com.zjl.collaboration.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DocPresenceService {

    /** docId → { sessionId → session } */
    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, SessionInfo>> docSessions = new ConcurrentHashMap<>();

    /** sessionId → 订阅的 docId 集合 */
    private final ConcurrentHashMap<String, Set<Long>> sessionDocs = new ConcurrentHashMap<>();

    /** 添加会话到文档房间 */
    public void join(Long docId, String sessionId, Long userId, String userName, WebSocketSession session) {
        docSessions.computeIfAbsent(docId, k -> new ConcurrentHashMap<>())
            .put(sessionId, new SessionInfo(userId, userName, session));
        log.debug("用户加入文档: docId={}, userId={}, onlineCount={}", docId, userId, getOnlineCount(docId));
    }

    /** 移除会话 */
    public void leave(Long docId, String sessionId) {
        ConcurrentHashMap<String, SessionInfo> sessions = docSessions.get(docId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                docSessions.remove(docId);
            }
        }
    }

    /** 获取文档的所有订阅者 */
    public Map<String, SessionInfo> getSubscribers(Long docId) {
        ConcurrentHashMap<String, SessionInfo> sessions = docSessions.get(docId);
        if (sessions == null) return Collections.emptyMap();
        return sessions;
    }

    /** 在线人数 */
    public int getOnlineCount(Long docId) {
        ConcurrentHashMap<String, SessionInfo> sessions = docSessions.get(docId);
        return sessions != null ? sessions.size() : 0;
    }

    /** 记录 session 订阅的文档 */
    public void trackSubscription(String sessionId, Long docId) {
        sessionDocs.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(docId);
    }

    /** 获取 session 订阅的所有文档并清理 */
    public Set<Long> removeSession(String sessionId) {
        Set<Long> docIds = sessionDocs.remove(sessionId);
        return docIds != null ? docIds : Collections.emptySet();
    }

    public record SessionInfo(Long userId, String userName, WebSocketSession session) {}
}
