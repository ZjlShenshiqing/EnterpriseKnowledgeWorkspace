package com.zjl.collaboration.service.impl;

import com.zjl.collaboration.service.DocPresenceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class DocPresenceServiceImpl implements DocPresenceService {

    private final ConcurrentHashMap<Long, ConcurrentHashMap<String, SessionInfo>> docSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<Long>> sessionDocs = new ConcurrentHashMap<>();

    @Override
    public void join(Long docId, String sessionId, Long userId, String userName, WebSocketSession session) {
        docSessions.computeIfAbsent(docId, k -> new ConcurrentHashMap<>())
            .put(sessionId, new SessionInfo(userId, userName, session));
        log.debug("用户加入文档: docId={}, userId={}, onlineCount={}", docId, userId, getOnlineCount(docId));
    }

    @Override
    public void leave(Long docId, String sessionId) {
        ConcurrentHashMap<String, SessionInfo> sessions = docSessions.get(docId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                docSessions.remove(docId);
            }
        }
    }

    @Override
    public Map<String, SessionInfo> getSubscribers(Long docId) {
        ConcurrentHashMap<String, SessionInfo> sessions = docSessions.get(docId);
        if (sessions == null) return Collections.emptyMap();
        return sessions;
    }

    @Override
    public int getOnlineCount(Long docId) {
        ConcurrentHashMap<String, SessionInfo> sessions = docSessions.get(docId);
        return sessions != null ? sessions.size() : 0;
    }

    @Override
    public void trackSubscription(String sessionId, Long docId) {
        sessionDocs.computeIfAbsent(sessionId, k -> ConcurrentHashMap.newKeySet()).add(docId);
    }

    @Override
    public Set<Long> removeSession(String sessionId) {
        Set<Long> docIds = sessionDocs.remove(sessionId);
        return docIds != null ? docIds : Collections.emptySet();
    }
}
