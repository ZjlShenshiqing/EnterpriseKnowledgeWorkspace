package com.zjl.knowledge.agent;

import com.zjl.knowledge.agent.entity.KbAgentSession;
import com.zjl.knowledge.agent.model.ChatMessage;
import java.util.List;
import java.util.Map;

/**
 * Agent 会话与消息持久化服务
 */
public interface AgentSessionService {

    KbAgentSession getOrCreateSession(Long sessionId, Long userId, String title);

    KbAgentSession requireSession(Long sessionId, Long userId);

    void saveUserMessage(Long sessionId, String content);

    Long saveAssistantMessage(Long sessionId, String content, Integer tokenCount);

    void saveToolMessage(Long sessionId, String toolName, Map<String, Object> input, Object output);

    List<ChatMessage> loadHistory(Long sessionId, int maxMessages);

    List<ChatMessage> loadHistoryForLlm(Long sessionId, int maxMessages);

    List<KbAgentSession> listUserSessions(Long userId);

    void archiveSession(Long sessionId, Long userId);
}
