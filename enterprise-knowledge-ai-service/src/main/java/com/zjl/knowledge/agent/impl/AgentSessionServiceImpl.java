package com.zjl.knowledge.agent.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.agent.AgentSessionService;
import com.zjl.knowledge.agent.entity.KbAgentMessage;
import com.zjl.knowledge.agent.entity.KbAgentSession;
import com.zjl.knowledge.agent.mapper.KbAgentMessageMapper;
import com.zjl.knowledge.agent.mapper.KbAgentSessionMapper;
import com.zjl.knowledge.agent.model.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class AgentSessionServiceImpl implements AgentSessionService {

    private final KbAgentSessionMapper sessionMapper;
    private final KbAgentMessageMapper messageMapper;

    @Override
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

    @Override
    public KbAgentSession requireSession(Long sessionId, Long userId) {
        if (sessionId == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        KbAgentSession session = sessionMapper.selectById(sessionId);
        if (session == null || !session.getUserId().equals(userId)) {
            throw new BizException(ErrorCode.NOT_FOUND, "会话不存在");
        }
        return session;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveUserMessage(Long sessionId, String content) {
        KbAgentMessage msg = new KbAgentMessage();
        msg.setSessionId(sessionId);
        msg.setRole("user");
        msg.setContent(content);
        msg.setCreatedAt(LocalDateTime.now());
        messageMapper.insert(msg);
    }

    @Override
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

    @Override
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

    @Override
    public List<ChatMessage> loadHistory(Long sessionId, int maxMessages) {
        return toChatMessages(fetchMessages(sessionId, maxMessages));
    }

    @Override
    public List<ChatMessage> loadHistoryForLlm(Long sessionId, int maxMessages) {
        List<KbAgentMessage> messages = fetchMessages(sessionId, maxMessages);
        List<ChatMessage> result = new ArrayList<>();
        for (KbAgentMessage m : messages) {
            if ("tool".equals(m.getRole())) {
                continue;
            }
            if ("assistant".equals(m.getRole()) && (m.getContent() == null || m.getContent().isBlank())) {
                continue;
            }
            result.add(toChatMessage(m));
        }
        return result;
    }

    @Override
    public List<KbAgentSession> listUserSessions(Long userId) {
        return sessionMapper.selectList(
                Wrappers.lambdaQuery(KbAgentSession.class)
                        .eq(KbAgentSession::getUserId, userId)
                        .eq(KbAgentSession::getStatus, "ACTIVE")
                        .orderByDesc(KbAgentSession::getUpdatedAt)
        );
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void archiveSession(Long sessionId, Long userId) {
        sessionMapper.update(null,
                Wrappers.lambdaUpdate(KbAgentSession.class)
                        .eq(KbAgentSession::getId, sessionId)
                        .eq(KbAgentSession::getUserId, userId)
                        .set(KbAgentSession::getStatus, "ARCHIVED")
                        .set(KbAgentSession::getUpdatedAt, LocalDateTime.now()));
    }

    private List<KbAgentMessage> fetchMessages(Long sessionId, int maxMessages) {
        List<KbAgentMessage> messages = messageMapper.selectList(
                Wrappers.lambdaQuery(KbAgentMessage.class)
                        .eq(KbAgentMessage::getSessionId, sessionId)
                        .orderByDesc(KbAgentMessage::getCreatedAt)
                        .last("LIMIT " + maxMessages)
        );
        Collections.reverse(messages);
        return messages;
    }

    private List<ChatMessage> toChatMessages(List<KbAgentMessage> messages) {
        List<ChatMessage> result = new ArrayList<>();
        for (KbAgentMessage m : messages) {
            result.add(toChatMessage(m));
        }
        return result;
    }

    private ChatMessage toChatMessage(KbAgentMessage m) {
        String content = m.getContent();
        if ("tool".equals(m.getRole()) && (content == null || content.isBlank())) {
            content = m.getToolOutput();
        }
        return ChatMessage.builder()
                .role(m.getRole())
                .content(content)
                .toolName(m.getToolName())
                .createdAt(m.getCreatedAt())
                .build();
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
