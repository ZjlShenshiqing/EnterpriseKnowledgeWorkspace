package com.zjl.knowledge.agent;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.knowledge.agent.entity.KbAgentSession;
import com.zjl.knowledge.web.UserContext;
import com.zjl.knowledge.web.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

/**
 * Agent 对话与会话管理接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/kb/agent")
@RequiredArgsConstructor
public class AgentController {

    private final AgentLoop agentLoop;
    private final AgentSessionService sessionService;

    /**
     * 对话接口（SSE 流式）。
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        UserContext user = UserContextHolder.get();
        com.zjl.knowledge.agent.SseEmitter emitter = new com.zjl.knowledge.agent.SseEmitter();

        // 异步执行 Agent 循环
        new Thread(() -> {
            try {
                // 保存用户消息
                KbAgentSession session = sessionService.getOrCreateSession(
                        request.getSessionId(), user.getUserId(),
                        truncate(request.getMessage(), 100));
                sessionService.saveUserMessage(session.getId(), request.getMessage());

                // 执行 Agent 循环
                agentLoop.run(session, user, emitter);
            } catch (Exception e) {
                log.error("Agent 对话异常", e);
                emitter.error("对话处理失败: " + e.getMessage());
            }
        }, "agent-chat-" + user.getUserId()).start();

        return emitter.getDelegate();
    }

    /**
     * 我的会话列表。
     */
    @GetMapping("/sessions")
    public Result<java.util.List<KbAgentSession>> listSessions() {
        UserContext user = UserContextHolder.get();
        return Results.success(sessionService.listUserSessions(user.getUserId()));
    }

    /**
     * 会话历史消息。
     */
    @GetMapping("/sessions/{id}")
    public Result<java.util.List<com.zjl.knowledge.agent.model.ChatMessage>> getSessionHistory(
            @PathVariable("id") Long id) {
        UserContext user = UserContextHolder.get();
        KbAgentSession session = sessionService.getOrCreateSession(id, user.getUserId(), null);
        return Results.success(sessionService.loadHistory(session.getId(), 200));
    }

    /**
     * 归档会话。
     */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> archiveSession(@PathVariable("id") Long id) {
        UserContext user = UserContextHolder.get();
        sessionService.archiveSession(id, user.getUserId());
        return Results.success();
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    /**
     * 对话请求。
     */
    @lombok.Data
    public static class ChatRequest {
        private Long sessionId;
        private String message;
    }
}
