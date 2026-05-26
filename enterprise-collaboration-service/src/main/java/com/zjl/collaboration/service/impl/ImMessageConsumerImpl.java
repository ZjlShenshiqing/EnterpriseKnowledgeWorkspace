package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.ImConversation;
import com.zjl.collaboration.entity.ImConversationMember;
import com.zjl.collaboration.entity.ImMessage;
import com.zjl.collaboration.mapper.ImConversationMapper;
import com.zjl.collaboration.mapper.ImConversationMemberMapper;
import com.zjl.collaboration.mapper.ImMessageMapper;
import com.zjl.collaboration.service.ImMessageConsumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@RocketMQMessageListener(topic = "im-message", consumerGroup = "im-consumer-group")
public class ImMessageConsumerImpl implements ImMessageConsumer, RocketMQListener<String> {

    private final ImMessageMapper msgMapper;
    private final ImConversationMapper convMapper;
    private final ImConversationMemberMapper memberMapper;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            Long messageId = node.get("messageId").asLong();
            Long conversationId = node.get("conversationId").asLong();
            Long senderId = node.get("senderId").asLong();
            String senderName = node.has("senderName") ? node.get("senderName").asText() : null;
            String content = node.has("content") ? node.get("content").asText() : "";

            ImMessage existing = msgMapper.selectById(messageId);
            if (existing != null) {
                existing.setStatus("SENT");
                existing.setCreatedAt(LocalDateTime.now());
                msgMapper.updateById(existing);
            }

            ImConversation conv = convMapper.selectById(conversationId);
            if (conv != null) {
                conv.setLastMsgContent(content.length() > 100 ? content.substring(0, 100) : content);
                conv.setLastMsgSender(senderName);
                conv.setLastMsgAt(LocalDateTime.now());
                conv.setUpdatedAt(LocalDateTime.now());
                convMapper.updateById(conv);
            }

            Map<String, Object> push = new LinkedHashMap<>();
            push.put("type", "message");
            push.put("id", messageId);
            push.put("conversationId", conversationId);
            push.put("senderId", senderId);
            push.put("senderName", senderName);
            push.put("content", content);
            push.put("status", "SENT");
            String json = objectMapper.writeValueAsString(push);

            List<ImConversationMember> members = memberMapper.selectList(
                    Wrappers.lambdaQuery(ImConversationMember.class)
                            .eq(ImConversationMember::getConversationId, conversationId));
            for (ImConversationMember m : members) {
                WebSocketSession s = ImMessageConsumer.onlineUsers.get(m.getUserId());
                if (s != null && s.isOpen()) {
                    try {
                        s.sendMessage(new TextMessage(json));
                    } catch (Exception e) {
                        log.warn("推送消息失败: userId={}, msgId={}", m.getUserId(), messageId);
                    }
                }
            }
        } catch (Exception e) {
            log.error("消费 IM 消息失败", e);
            throw new RuntimeException("消费失败，触发 RocketMQ 重试", e);
        }
    }
}
