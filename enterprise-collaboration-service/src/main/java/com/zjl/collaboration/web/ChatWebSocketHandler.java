package com.zjl.collaboration.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Map<Long, WebSocketSession> onlineUsers = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JwtUtil jwtUtil;
    private final JdbcTemplate jdbc;

    public ChatWebSocketHandler(JwtUtil jwtUtil, JdbcTemplate jdbc) {
        this.jwtUtil = jwtUtil; this.jdbc = jdbc;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId != null) {
            onlineUsers.put(userId, session);
            broadcastStatus(userId, "online");
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long senderId = getUserId(session);
        if (senderId == null) return;

        Map<String, Object> msg = mapper.readValue(message.getPayload(), Map.class);
        Long convId = Long.valueOf(msg.get("conversationId").toString());
        String content = msg.get("content").toString();

        var sender = jdbc.queryForMap("SELECT id, real_name FROM sys_user WHERE id=?", senderId);

        jdbc.update(
            "INSERT INTO im_message (conversation_id, sender_id, sender_name, content, created_at) VALUES (?,?,?,?,?)",
            convId, senderId, sender.get("real_name"), content, LocalDateTime.now());
        jdbc.update("UPDATE im_conversation SET updated_at=? WHERE id=?", LocalDateTime.now(), convId);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "message");
        out.put("conversationId", convId);
        out.put("senderId", senderId);
        out.put("senderName", sender.get("real_name"));
        out.put("content", content);
        String json = mapper.writeValueAsString(out);

        var members = jdbc.queryForList(
            "SELECT user_id FROM im_conversation_member WHERE conversation_id=?", convId);
        for (var m : members) {
            Long uid = ((Number) m.get("user_id")).longValue();
            WebSocketSession s = onlineUsers.get(uid);
            if (s != null && s.isOpen()) s.sendMessage(new TextMessage(json));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = getUserId(session);
        if (userId != null) { onlineUsers.remove(userId); broadcastStatus(userId, "offline"); }
    }

    private void broadcastStatus(Long userId, String status) {
        Map<String, Object> out = Map.of("type", "status", "userId", userId, "status", status);
        try {
            String json = mapper.writeValueAsString(out);
            for (var s : onlineUsers.values()) {
                if (s.isOpen()) s.sendMessage(new TextMessage(json));
            }
        } catch (Exception ignored) {}
    }

    private Long getUserId(WebSocketSession session) {
        String token = session.getUri().getQuery();
        if (token != null && token.startsWith("token=")) {
            try {
                Claims c = jwtUtil.parse(token.substring(6));
                return c.get("userId", Long.class);
            } catch (Exception ignored) {}
        }
        return null;
    }
}
