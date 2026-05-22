package com.zjl.collaboration.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.SysUser;
import com.zjl.collaboration.mapper.SysUserMapper;
import com.zjl.collaboration.service.ImMessageConsumer;
import com.zjl.collaboration.service.ImMessageService;
import com.zjl.collaboration.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private final ObjectMapper mapper = new ObjectMapper();
    private final JwtUtil jwtUtil;
    private final ImMessageService imMessageService;
    private final SysUserMapper userMapper;

    public ChatWebSocketHandler(JwtUtil jwtUtil, ImMessageService imMessageService,
                                 SysUserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.imMessageService = imMessageService;
        this.userMapper = userMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId != null) {
            ImMessageConsumer.onlineUsers.put(userId, session);
            broadcastStatus(userId, "online");
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long senderId = getUserId(session);
        if (senderId == null) return;
        Map<String, Object> msg = mapper.readValue(message.getPayload(), Map.class);
        Long convId = Long.valueOf(msg.get("conversationId").toString());
        String content = msg.get("content") != null ? msg.get("content").toString() : "";
        String clientMsgId = msg.get("clientMsgId") != null ? msg.get("clientMsgId").toString() : "";

        SysUser sender = userMapper.selectById(senderId);
        String senderName = sender != null ? sender.getRealName() : null;

        Map<String, Object> ack = imMessageService.send(senderId, senderName, convId, content, clientMsgId);
        String ackJson = mapper.writeValueAsString(ack);
        session.sendMessage(new TextMessage(ackJson));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) {
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
        String query = session.getUri().getQuery();
        if (query != null && query.startsWith("token=")) {
            try {
                Claims c = jwtUtil.parse(query.substring(6));
                return c.get("userId", Long.class);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
