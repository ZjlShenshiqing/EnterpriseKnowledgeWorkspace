package com.zjl.knowledge.agent;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.knowledge.agent.entity.KbAgentMessage;
import com.zjl.knowledge.agent.entity.KbAgentSession;
import com.zjl.knowledge.agent.mapper.KbAgentMessageMapper;
import com.zjl.knowledge.agent.mapper.KbAgentSessionMapper;
import com.zjl.knowledge.agent.model.ChatMessage;
import com.zjl.knowledge.agent.model.ToolCall;
import com.zjl.knowledge.agent.mcp.ToolResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Agent 会话与消息持久化服务
 */
@Service
@RequiredArgsConstructor
public class AgentSessionService {

    private final KbAgentSessionMapper sessionMapper;
    private final KbAgentMessageMapper messageMapper;

    /**
     * 创建或加载会话
     *
     * @param sessionId 会话 ID（null 则创建新会话）
     * @param userId    用户 ID
     * @param title     会话标题（新会话时使用）
     * @return 会话
     */
    @Transactional(rollbackFor = Exception.class)
    public KbAgentSession getOrCreateSession(Long sessionId, Long userId, String title) {
        if (sessionId != null) {
            KbAgentSession session = sessionMapper.selectById(sessionId);
            if (session != null && session.getUserId().equals(userId)) {
                return session;
            }
        }

        KbAgentSession session = new KbAgentSession();
        session.setUserId(userId);
        session.setTitle(title != null && title.length() > 100
                ? title.substring(0, 100) : title);
        session.setStatus("ACTIVE");
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());
        sessionMapper.insert(session);
        return session;
    }

    /**
     * 保存用户消息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveUserMessage(Long sessionId, String content) {
        KbAgentMessage msg = new KbAgentMessage();
        msg.setSessionId(sessionId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    /**
     * 保存助手消息
     */
    @Transactional(rollbackFor = Exception.class)
    public Long saveAssistantMessage(Long sessionId, String content, Integer tokenCount) {
        KbAgentMessage msg = new KbAgentMessage();
        msg.setSessionId(sessionId);
        msg.setRole("assistant");
        msg.setContent(content);
        msg.setTokenCount(tokenCount);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
        return msg.getId();
    }

    /**
     * 保存 tool 调用消息
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveToolMessage(Long sessionId, String toolName,
                                 Map<String, Object> input, Object output) {
        KbAgentMessage msg = new KbAgentMessage();
        msg.setSessionId(sessionId);
        msg.setRole("tool");
        msg.setToolName(toolName);
        msg.setToolInput(toJson(input));
        msg.setToolOutput(toJson(output));
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    /**
     * 加载会话历史消息，转为 ChatMessage 列表
     */
    public List<ChatMessage> loadHistory(Long sessionId, int maxMessages) {
        List<KbAgentMessage> messages = messageMapper.selectList(
                Wrappers.lambdaQuery(KbAgentMessage.class)
                        .eq(KbAgentMessage::getSessionId, sessionId)
                        .orderByAsc(KbAgentMessage::getCreatedAt)
                        .last("LIMIT " + maxMessages)
        );

        List<ChatMessage> result = new ArrayList<>();
        for (KbAgentMessage m : messages) {
            result.add(ChatMessage.builder()
                    .role(m.getRole())
                    .content(m.getContent())
                    .toolName(m.getToolName())
                    .build());
        }
        return result;
    }

    /**
     * 查询用户的会话列表（按更新时间倒序）
     */
    public List<KbAgentSession> listUserSessions(Long userId) {
        return sessionMapper.selectList(
                Wrappers.lambdaQuery(KbAgentSession.class)
                        .eq(KbAgentSession::getUserId, userId)
                        .eq(KbAgentSession::getStatus, "ACTIVE")
                        .orderByDesc(KbAgentSession::getUpdatedAt)
        );
    }

    /**
     * 归档会话
     */
    @Transactional(rollbackFor = Exception.class)
    public void archiveSession(Long sessionId, Long userId) {
        sessionMapper.update(null,
                Wrappers.lambdaUpdate(KbAgentSession.class)
                        .eq(KbAgentSession::getId, sessionId)
                        .eq(KbAgentSession::getUserId, userId)
                        .set(KbAgentSession::getStatus, "ARCHIVED")
                        .set(KbAgentSession::getUpdatedAt, LocalDateTime.now()));
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return null;
        }
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }
}
