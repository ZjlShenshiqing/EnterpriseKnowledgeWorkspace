package com.zjl.collaboration.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.ImMessage;
import com.zjl.collaboration.mapper.ImMessageMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ImMessageService {

    private final ImMessageMapper msgMapper;
    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 发送消息：先持久化（status=SENDING），再投递到 RocketMQ。
     * 返回 ACK payload。
     */
    public Map<String, Object> send(Long senderId, String senderName, Long conversationId,
                                     String content, String clientMsgId) {
        ImMessage msg = new ImMessage();
        msg.setConversationId(conversationId);
        msg.setSenderId(senderId);
        msg.setSenderName(senderName);
        msg.setContent(content);
        msg.setMsgType("text");
        msg.setStatus("SENDING");
        msg.setCreatedAt(LocalDateTime.now());
        msgMapper.insert(msg);

        try {
            Map<String, Object> mqPayload = new LinkedHashMap<>();
            mqPayload.put("messageId", msg.getId());
            mqPayload.put("conversationId", conversationId);
            mqPayload.put("senderId", senderId);
            mqPayload.put("senderName", senderName);
            mqPayload.put("content", content);
            mqPayload.put("createdAt", msg.getCreatedAt().toString());

            String mqMsgId = rocketMQTemplate.syncSend("im-message",
                    MessageBuilder.withPayload(objectMapper.writeValueAsString(mqPayload)).build(),
                    3000).getMsgId();

            msg.setMqMsgId(mqMsgId);
            msg.setStatus("SENT");
            msgMapper.updateById(msg);
        } catch (Exception e) {
            log.error("RocketMQ 投递失败: msgId={}", msg.getId(), e);
            msg.setStatus("FAILED");
            msgMapper.updateById(msg);
            Map<String, Object> ack = new LinkedHashMap<>();
            ack.put("type", "ack");
            ack.put("clientMsgId", clientMsgId);
            ack.put("serverMsgId", msg.getId());
            ack.put("status", "FAILED");
            return ack;
        }

        Map<String, Object> ack = new LinkedHashMap<>();
        ack.put("type", "ack");
        ack.put("clientMsgId", clientMsgId);
        ack.put("serverMsgId", msg.getId());
        ack.put("status", "SENT");
        return ack;
    }
}
