package com.zjl.collaboration.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.integration.GatewayUserClient;
import com.zjl.collaboration.integration.UserInfo;
import com.zjl.collaboration.service.ImMessageConsumer;
import com.zjl.collaboration.service.ImMessageService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Slf4j
@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final CloseStatus AUTH_FAILED = new CloseStatus(4002, "用户未认证");

    private final ObjectMapper mapper = new ObjectMapper();
    private final ImMessageService imMessageService;
    private final GatewayUserClient gatewayUserClient;

    public ChatWebSocketHandler(ImMessageService imMessageService,
                                 GatewayUserClient gatewayUserClient) {
        this.imMessageService = imMessageService;
        this.gatewayUserClient = gatewayUserClient;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        Long userId = getUserId(session);
        if (userId == null) {
            session.close(AUTH_FAILED);
            return;
        }
        log.info("Chat WS连接: userId={}", userId);
        ImMessageConsumer.onlineUsers.put(userId, session);
        broadcastStatus(userId, "online");
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long senderId = getUserId(session);
        if (senderId == null) return;
        Map<String, Object> msg = mapper.readValue(message.getPayload(), Map.class);
        Long convId = Long.valueOf(msg.get("conversationId").toString());
        String content = msg.get("content") != null ? msg.get("content").toString() : "";
        String clientMsgId = msg.get("clientMsgId") != null ? msg.get("clientMsgId").toString() : "";

        UserInfo sender = gatewayUserClient.getById(senderId);
        String senderName = sender != null ? sender.realName() : null;

        Map<String, Object> ack = imMessageService.send(senderId, senderName, convId, content, clientMsgId);
        String ackJson = mapper.writeValueAsString(ack);
        session.sendMessage(new TextMessage(ackJson));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) {
            log.info("Chat WS断开: userId={}", userId);
            ImMessageConsumer.onlineUsers.remove(userId);
            broadcastStatus(userId, "offline");
        }
    }

    private void broadcastStatus(Long userId, String status) {
        Map<String, Object> out = Map.of("type", "status", "userId", userId, "status", status);
        try {
            String json = mapper.writeValueAsString(out);
            for (var s : ImMessageConsumer.onlineUsers.values()) {
                if (s.isOpen()) s.sendMessage(new TextMessage(json));
            }
        } catch (Exception ignored) {}
    }

    private Long getUserId(WebSocketSession session) {
        String userIdHeader = session.getHandshakeHeaders().getFirst("X-User-Id");
        if (userIdHeader != null && !userIdHeader.isBlank()) {
            return Long.parseLong(userIdHeader);
        }
        return null;
    }
}
