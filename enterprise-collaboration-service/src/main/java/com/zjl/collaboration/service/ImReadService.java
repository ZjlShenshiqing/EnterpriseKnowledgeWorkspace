package com.zjl.collaboration.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.ImMessage;
import com.zjl.collaboration.entity.ImMessageRead;
import com.zjl.collaboration.mapper.ImMessageMapper;
import com.zjl.collaboration.mapper.ImMessageReadMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImReadService {

    private final ImMessageReadMapper readMapper;
    private final ImMessageMapper msgMapper;
    private final ObjectMapper objectMapper;

    public void markRead(Long userId, Long conversationId, Long lastReadMsgId) {
        ImMessageRead record = readMapper.selectOne(
                Wrappers.lambdaQuery(ImMessageRead.class)
                        .eq(ImMessageRead::getUserId, userId)
                        .eq(ImMessageRead::getConversationId, conversationId));

        if (record == null) {
            record = new ImMessageRead();
            record.setUserId(userId);
            record.setConversationId(conversationId);
            record.setLastReadMsgId(lastReadMsgId);
            readMapper.insert(record);
        } else {
            if (lastReadMsgId > record.getLastReadMsgId()) {
                record.setLastReadMsgId(lastReadMsgId);
                record.setUpdatedAt(LocalDateTime.now());
                readMapper.updateById(record);
            }
        }

        Map<String, Object> readNotify = new LinkedHashMap<>();
        readNotify.put("type", "read");
        readNotify.put("conversationId", conversationId);
        readNotify.put("userId", userId);
        readNotify.put("lastReadMsgId", lastReadMsgId);

        try {
            String json = objectMapper.writeValueAsString(readNotify);
            List<ImMessage> messages = msgMapper.selectList(
                    Wrappers.lambdaQuery(ImMessage.class)
                            .eq(ImMessage::getConversationId, conversationId)
                            .orderByDesc(ImMessage::getCreatedAt));
            for (ImMessage msg : messages) {
                if (!msg.getSenderId().equals(userId)) {
                    WebSocketSession s = ImMessageConsumer.onlineUsers.get(msg.getSenderId());
                    if (s != null && s.isOpen()) {
                        try { s.sendMessage(new TextMessage(json)); } catch (Exception ignored) {}
                    }
                }
            }
        } catch (Exception e) {
            log.warn("已读通知推送失败: userId={}, convId={}", userId, conversationId);
        }
    }

    public int unreadCount(Long userId, Long conversationId) {
        ImMessageRead record = readMapper.selectOne(
                Wrappers.lambdaQuery(ImMessageRead.class)
                        .eq(ImMessageRead::getUserId, userId)
                        .eq(ImMessageRead::getConversationId, conversationId));
        long lastReadId = record != null ? record.getLastReadMsgId() : 0L;
        return (int) msgMapper.selectCount(
                Wrappers.lambdaQuery(ImMessage.class)
                        .eq(ImMessage::getConversationId, conversationId)
                        .gt(ImMessage::getId, lastReadId));
    }
}
