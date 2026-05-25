package com.zjl.collaboration.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zjl.collaboration.service.DocOTService;
import com.zjl.collaboration.service.DocPermissionService;
import com.zjl.collaboration.service.DocPermissionService.Permission;
import com.zjl.collaboration.service.DocPresenceService;
import com.zjl.collaboration.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * WebSocket 处理器，用于文档协同编辑，
 * 支持 OT 操作同步、光标位置广播和在线状态推送。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocWebSocketHandler extends TextWebSocketHandler {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final DocOTService docOTService;
    private final DocPresenceService presenceService;
    private final DocPermissionService permissionService;
    private final JwtUtil jwtUtil;

    private final Map<String, UserContext> sessionUsers = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        try {
            URI uri = session.getUri();
            if (uri == null || uri.getQuery() == null) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            String query = uri.getQuery();
            String token = null;
            for (String param : query.split("&")) {
                if (param.startsWith("token=")) {
                    token = param.substring(6);
                    break;
                }
            }
            if (token == null) {
                session.close(CloseStatus.BAD_DATA);
                return;
            }

            Claims claims = jwtUtil.parse(token);
            Long userId = claims.get("userId", Long.class);
            String userName = claims.get("realName", String.class);
            sessionUsers.put(session.getId(), new UserContext(userId, userName));
            log.info("WS连接建立: sessionId={}, userId={}", session.getId(), userId);
        } catch (Exception e) {
            log.error("WebSocket 认证失败: session={}", session.getId(), e);
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (Exception ignored) {
            }
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode msg = OBJECT_MAPPER.readTree(message.getPayload());
            String action = msg.path("action").asText();
            UserContext user = sessionUsers.get(session.getId());
            if (user == null) {
                sendError(session, "未认证");
                return;
            }

            switch (action) {
                case "sub" -> handleSubscribe(session, msg, user);
                case "op" -> handleOperation(session, msg, user);
                case "cursor" -> handleCursor(session, msg, user);
                case "presence" -> handlePresence(session, msg, user);
                default -> log.warn("未知 action 类型: {}", action);
            }
        } catch (Exception e) {
            log.error("处理 WebSocket 消息失败", e);
        }
    }

    private void handleSubscribe(WebSocketSession session, JsonNode msg, UserContext user) {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) {
            sendError(session, "docId 不能为空");
            return;
        }

        Permission perm = permissionService.checkPermission(docId, user.userId(), null);
        if (perm == null) {
            log.warn("WS订阅拒绝: docId={}, userId={}", docId, user.userId());
            sendError(session, "无权访问此文档");
            return;
        }

        presenceService.join(docId, session.getId(), user.userId(), user.userName(), session);
        presenceService.trackSubscription(session.getId(), docId);

        DocOTService.DocSnapshot snapshot = docOTService.getDocument(docId);
        if (snapshot != null) {
            ObjectNode initMsg = OBJECT_MAPPER.createObjectNode();
            initMsg.put("action", "init");
            initMsg.put("docId", docId);
            initMsg.put("content", snapshot.content());
            initMsg.put("version", snapshot.version());
            initMsg.put("permission", perm.name());
            send(session, initMsg);
        }

        broadcastPresence(docId, user.userId(), user.userName(), true, session.getId());
    }

    private void handleOperation(WebSocketSession session, JsonNode msg, UserContext user) throws Exception {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) {
            sendError(session, "docId 不能为空");
            return;
        }

        int baseVersion = msg.path("version").asInt();
        JsonNode ops = msg.path("ops");

        if (!ops.isArray() || ops.isEmpty()) {
            sendError(session, "ops 不能为空");
            return;
        }

        try {
            JsonNode transformedOps = docOTService.submitOperation(docId, user.userId(), ops, baseVersion);

            ObjectNode ack = OBJECT_MAPPER.createObjectNode();
            ack.put("action", "ack");
            ack.put("docId", docId);
            ack.put("version", baseVersion + 1);
            send(session, ack);

            ObjectNode broadcast = OBJECT_MAPPER.createObjectNode();
            broadcast.put("action", "op");
            broadcast.put("docId", docId);
            broadcast.set("ops", transformedOps);
            broadcast.put("version", baseVersion + 1);
            broadcast.put("userId", user.userId());

            for (var entry : presenceService.getSubscribers(docId).entrySet()) {
                if (!entry.getKey().equals(session.getId())) {
                    send(entry.getValue().session(), broadcast);
                }
            }
        } catch (Exception e) {
            log.error("OT 操作处理失败: docId={}, version={}", docId, baseVersion, e);
            sendError(session, "操作冲突，请刷新页面");
        }
    }

    private void handleCursor(WebSocketSession session, JsonNode msg, UserContext user) {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) return;

        ObjectNode cursorMsg = OBJECT_MAPPER.createObjectNode();
        cursorMsg.put("action", "cursor");
        cursorMsg.put("docId", docId);
        cursorMsg.put("userId", user.userId());
        cursorMsg.put("userName", user.userName());
        cursorMsg.set("range", msg.path("range"));

        for (var entry : presenceService.getSubscribers(docId).entrySet()) {
            if (!entry.getKey().equals(session.getId())) {
                send(entry.getValue().session(), cursorMsg);
            }
        }
    }

    private void handlePresence(WebSocketSession session, JsonNode msg, UserContext user) {
        Long docId = msg.path("docId").asLong();
        if (docId == 0) return;
        broadcastPresence(docId, user.userId(), user.userName(), msg.path("online").asBoolean(true), session.getId());
    }

    private void broadcastPresence(Long docId, Long userId, String userName, boolean online, String excludeSessionId) {
        ObjectNode presenceMsg = OBJECT_MAPPER.createObjectNode();
        presenceMsg.put("action", "presence");
        presenceMsg.put("docId", docId);
        presenceMsg.put("userId", userId);
        presenceMsg.put("userName", userName);
        presenceMsg.put("online", online);

        for (var entry : presenceService.getSubscribers(docId).entrySet()) {
            if (!entry.getKey().equals(excludeSessionId)) {
                send(entry.getValue().session(), presenceMsg);
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        UserContext user = sessionUsers.remove(session.getId());
        if (user == null) {
            log.info("WS连接断开: sessionId={}", session.getId());
            return;
        }
        log.info("WS连接断开: sessionId={}, userId={}", session.getId(), user.userId());

        Set<Long> docIds = presenceService.removeSession(session.getId());
        for (Long docId : docIds) {
            presenceService.leave(docId, session.getId());
            broadcastPresence(docId, user.userId(), user.userName(), false, session.getId());
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.error("WebSocket 传输错误: session={}", session.getId(), exception);
    }

    private void send(WebSocketSession session, JsonNode message) {
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(OBJECT_MAPPER.writeValueAsString(message)));
            }
        } catch (Exception e) {
            log.error("发送消息失败: session={}", session.getId(), e);
        }
    }

    private void sendError(WebSocketSession session, String errMsg) {
        try {
            ObjectNode error = OBJECT_MAPPER.createObjectNode();
            error.put("action", "error");
            error.put("message", errMsg);
            send(session, error);
        } catch (Exception ignored) {
        }
    }

    private record UserContext(Long userId, String userName) {
    }
}
