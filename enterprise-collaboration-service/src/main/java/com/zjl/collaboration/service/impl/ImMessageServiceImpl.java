package com.zjl.collaboration.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.ImConversation;
import com.zjl.collaboration.entity.ImConversationMember;
import com.zjl.collaboration.entity.ImMessage;
import com.zjl.collaboration.mapper.ImConversationMapper;
import com.zjl.collaboration.mapper.ImConversationMemberMapper;
import com.zjl.collaboration.mapper.ImMessageMapper;
import com.zjl.collaboration.service.ImMessageConsumer;
import com.zjl.collaboration.service.ImMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
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
public class ImMessageServiceImpl implements ImMessageService {

    private final ImMessageMapper msgMapper;
    private final ImConversationMapper convMapper;
    private final ImConversationMemberMapper memberMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public Map<String, Object> send(Long senderId, String senderName, Long conversationId,
                                     String content, String clientMsgId) {
        ImMessage msg = new ImMessage();
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setSenderName(senderName);
        msg.setContent(content);
        msg.setMsgType("text");
        msg.setStatus("SENT");
        msg.setCreatedAt(LocalDateTime.now());
        msgMapper.insert(msg);

        ImConversation conv = convMapper.selectById(conversationId);
        if (conv != null) {
            conv.setLastMsgContent(content.length() > 100 ? content.substring(0, 100) : content);
            conv.setLastMsgSender(senderName);
            conv.setLastMsgAt(LocalDateTime.now());
            conv.setUpdatedAt(LocalDateTime.now());
            convMapper.updateById(conv);
        }

        Map<String, Object> pushPayload = new LinkedHashMap<>();
        pushPayload.put("type", "message");
        pushPayload.put("id", msg.getId());
        pushPayload.put("conversationId", conversationId);
        pushPayload.put("senderId", senderId);
        pushPayload.put("senderName", senderName);
        pushPayload.put("content", content);
        pushPayload.put("status", "SENT");
        pushPayload.put("createdAt", msg.getCreatedAt().toString());

        try {
            String pushJson = objectMapper.writeValueAsString(pushPayload);
            List<ImConversationMember> members = memberMapper.selectList(
                    Wrappers.lambdaQuery(ImConversationMember.class)
                            .eq(ImConversationMember::getConversationId, conversationId));
            for (ImConversationMember m : members) {
                WebSocketSession s = ImMessageConsumer.onlineUsers.get(m.getUserId());
                if (s != null && s.isOpen()) {
                    try { s.sendMessage(new TextMessage(pushJson)); } catch (Exception ignored) {}
                }
            }
        } catch (Exception e) {
            log.warn("在线推送失败: convId={}", conversationId);
        }

        try {
            Map<String, Object> mqPayload = new LinkedHashMap<>();
            mqPayload.put("messageId", msg.getId());
            mqPayload.put("conversationId", conversationId);
            mqPayload.put("senderId", senderId);
            mqPayload.put("senderName", senderName);
            mqPayload.put("content", content);
            mqPayload.put("createdAt", msg.getCreatedAt().toString());
            rocketMQTemplate.asyncSend("im-message",
                    MessageBuilder.withPayload(objectMapper.writeValueAsString(mqPayload)).build(),
                    new org.apache.rocketmq.client.producer.SendCallback() {
                        @Override
                        public void onSuccess(org.apache.rocketmq.client.producer.SendResult sendResult) {
                            msg.setMqMsgId(sendResult.getMsgId());
                            msgMapper.updateById(msg);
                        }
                        @Override
                        public void onException(Throwable e) {
                            log.warn("RocketMQ 异步投递失败: msgId={}", msg.getId());
                        }
                    });
        } catch (Exception e) {
            log.warn("RocketMQ 不可用，跳过 MQ 投递: msgId={}", msg.getId());
        }

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "ack");
        ack.put("clientMsgId", clientMsgId);
        ack.put("serverMsgId", msg.getId());
        ack.put("status", "SENT");
        return ack;
    }
}
