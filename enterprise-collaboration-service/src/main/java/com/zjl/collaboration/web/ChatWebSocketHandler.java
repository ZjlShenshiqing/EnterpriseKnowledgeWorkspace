package com.zjl.collaboration.web;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.collaboration.entity.*;
import com.zjl.collaboration.mapper.*;
import com.zjl.collaboration.util.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ChatWebSocketHandler extends TextWebSocketHandler {

    private static final Map<Long, WebSocketSession> onlineUsers = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();
    private final JwtUtil jwtUtil;
    private final ImMessageMapper msgMapper;
    private final ImConversationMapper convMapper;
    private final ImConversationMemberMapper memberMapper;
    private final SysUserMapper userMapper;

    public ChatWebSocketHandler(JwtUtil jwtUtil, ImMessageMapper msgMapper, ImConversationMapper convMapper, ImConversationMemberMapper memberMapper, SysUserMapper userMapper) {
        this.jwtUtil = jwtUtil; this.msgMapper = msgMapper; this.convMapper = convMapper; this.memberMapper = memberMapper; this.userMapper = userMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = getUserId(session);
        if (userId != null) { onlineUsers.put(userId, session); broadcastStatus(userId, "online"); }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Long senderId = getUserId(session);
        if (senderId == null) return;
        Map<String, Object> msg = mapper.readValue(message.getPayload(), Map.class);
        Long convId = Long.valueOf(msg.get("conversationId").toString());
        String content = msg.get("content").toString();

        SysUser sender = userMapper.selectById(senderId);
        ImMessage im = new ImMessage(); im.setConversationId(convId); im.setSenderId(senderId);
        im.setSenderName(sender != null ? sender.getRealName() : null); im.setContent(content);
        im.setCreatedAt(LocalDateTime.now());
        msgMapper.insert(im);

        ImConversation conv = convMapper.selectById(convId);
        if (conv != null) { conv.setUpdatedAt(LocalDateTime.now()); convMapper.updateById(conv); }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("type", "message"); out.put("conversationId", convId);
        out.put("senderId", senderId); out.put("senderName", sender != null ? sender.getRealName() : null); out.put("content", content);
        String json = mapper.writeValueAsString(out);

        List<ImConversationMember> members = memberMapper.selectList(Wrappers.lambdaQuery(ImConversationMember.class).eq(ImConversationMember::getConversationId, convId));
        for (ImConversationMember m : members) {
            WebSocketSession s = onlineUsers.get(m.getUserId());
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
            for (var s : onlineUsers.values()) { if (s.isOpen()) s.sendMessage(new TextMessage(json)); }
        } catch (Exception ignored) {}
    }

    private Long getUserId(WebSocketSession session) {
        String token = session.getUri().getQuery();
        if (token != null && token.startsWith("token=")) {
            try { Claims c = jwtUtil.parse(token.substring(6)); return c.get("userId", Long.class); } catch (Exception ignored) {}
        }
        return null;
    }
}
