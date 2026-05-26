package com.zjl.knowledge.agent;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.knowledge.agent.entity.KbAgentSession;
import com.zjl.knowledge.service.FileStorageService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

/**
 * Agent 对话与会话管理接口
 *
 * <p>提供 AI Agent 对话交互和会话管理功能，支持流式响应和多轮对话</p>
 *
 * <p>主要功能：</p>
 * <ul>
 *   <li>流式对话接口 - SSE 实时推送响应</li>
 *   <li>会话列表管理 - 获取用户的所有对话会话</li>
 *   <li>会话历史查询 - 获取指定会话的历史消息</li>
 *   <li>会话归档 - 删除/归档会话</li>
 * </ul>
 *
 * @see AgentLoop
 * @see AgentSessionService
 */
@Slf4j
@RestController
@RequestMapping("/api/kb/agent")
@RequiredArgsConstructor
public class AgentController {

    /**
     * Agent 核心循环组件，负责执行对话逻辑和工具调用
     */
    private final AgentLoop agentLoop;

    /**
     * 会话服务，管理会话的创建、查询和消息存储
     */
    private final AgentSessionService sessionService;

    /**
     * 文件存储服务，用于聊天附件上传
     */
    private final FileStorageService fileStorageService;

    /**
     * 对话接口（SSE 流式响应）
     *
     * <p>接收用户消息，触发 Agent 循环，通过 SSE 流式返回响应。
     * 支持多轮对话，通过 sessionId 关联上下文。</p>
     *
     * <p>请求示例：</p>
     * <pre>
     * POST /api/kb/agent/chat
     * {
     *   "sessionId": 123,      // 可选，不传则创建新会话
     *   "message": "你好"      // 用户输入
     * }
     * </pre>
     *
     * <p>SSE 响应事件：</p>
     * <ul>
     *   <li>event: text - 文本片段，实时推送</li>
     *   <li>event: tool_call - 工具调用通知</li>
     *   <li>event: done - 对话结束标记</li>
     * </ul>
     *
     * @param request 对话请求，包含会话ID和消息内容
     * @return SSE 流式响应
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter chat(@RequestBody ChatRequest request) {
        UserContext user = UserContextHolder.get();
        AgentSseEmitter emitter = new AgentSseEmitter();

        // 异步执行 Agent 循环，避免阻塞 HTTP 线程
        new Thread(() -> {
            try {
                // 获取或创建会话，使用消息前100字符作为标题
                KbAgentSession session = sessionService.getOrCreateSession(
                        request.getSessionId(), user.getUserId(),
                        truncate(request.getMessage(), 100));
                
                // 保存用户消息到历史记录
                sessionService.saveUserMessage(session.getId(), buildUserMessageText(request, user));

                // 执行 Agent 核心循环（LLM 调用 + 工具调用）
                agentLoop.run(session, user, emitter, request.isWebSearch());
            } catch (Exception e) {
                log.error("Agent 对话异常", e);
                emitter.error("对话处理失败: " + e.getMessage());
            }
        }, "agent-chat-" + user.getUserId()).start();

        return emitter.getDelegate();
    }

    /**
     * 获取当前用户的会话列表
     *
     * <p>按更新时间倒序返回用户的所有对话会话，用于会话列表展示。</p>
     *
     * <p>响应示例：</p>
     * <pre>
     * {
     *   "code": 0,
     *   "data": [
     *     {
     *       "id": 123,
     *       "title": "关于 Spring Boot 的问题",
     *       "status": "ACTIVE",
     *       "createdAt": "2024-01-01T10:00:00",
     *       "updatedAt": "2024-01-01T10:05:00"
     *     }
     *   ]
     * }
     * </pre>
     *
     * @return 会话列表
     */
    @GetMapping("/sessions")
    public Result<java.util.List<KbAgentSession>> listSessions() {
        UserContext user = UserContextHolder.get();
        return Results.success(sessionService.listUserSessions(user.getUserId()));
    }

    /**
     * 获取会话历史消息
     *
     * <p>获取指定会话的历史消息记录，最多返回最近 200 条消息。</p>
     *
     * <p>路径参数：</p>
     * <ul>
     *   <li>id - 会话 ID</li>
     * </ul>
     *
     * <p>响应示例：</p>
     * <pre>
     * {
     *   "code": 0,
     *   "data": [
     *     {
     *       "role": "user",
     *       "content": "你好"
     *     },
     *     {
     *       "role": "assistant",
     *       "content": "您好！我是您的智能助手。"
     *     }
     *   ]
     * }
     * </pre>
     *
     * @param id 会话 ID
     * @return 历史消息列表
     */
    @GetMapping("/sessions/{id}")
    public Result<java.util.List<com.zjl.knowledge.agent.model.ChatMessage>> getSessionHistory(
            @PathVariable("id") Long id) {
        UserContext user = UserContextHolder.get();
        sessionService.requireSession(id, user.getUserId());
        return Results.success(sessionService.loadHistory(id, 200));
    }

    /**
     * 归档（删除）会话
     *
     * <p>将指定会话标记为已归档状态，会话及其历史消息将被保留但不再显示在会话列表中。</p>
     *
     * <p>路径参数：</p>
     * <ul>
     *   <li>id - 会话 ID</li>
     * </ul>
     *
     * @param id 会话 ID
     * @return 操作成功结果
     */
    @DeleteMapping("/sessions/{id}")
    public Result<Void> archiveSession(@PathVariable("id") Long id) {
        UserContext user = UserContextHolder.get();
        sessionService.archiveSession(id, user.getUserId());
        return Results.success();
    }

    /**
     * 聊天文件上传
     *
     * <p>上传聊天附件文件到 OSS/S3，返回文件信息供对话引用。</p>
     *
     * @param file 上传文件
     * @return 文件信息（name, size, url）
     */
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Result<Map<String, Object>> uploadAttachment(@RequestParam("file") MultipartFile file) {
        UserContext user = UserContextHolder.get();
        if (file == null || file.isEmpty()) {
            return Results.success(Map.of("error", "文件不能为空"));
        }
        try {
            long docId = System.currentTimeMillis();
            String originalName = file.getOriginalFilename();
            if (originalName == null || originalName.isBlank()) {
                originalName = "file";
            }
            String storedPath = fileStorageService.store(docId, originalName, file.getInputStream());
            log.info("聊天附件已上传: userId={}, name={}, path={}, size={}",
                    user.getUserId(), originalName, storedPath, file.getSize());

            Map<String, Object> result = Map.of(
                    "name", originalName,
                    "size", file.getSize(),
                    "path", storedPath
            );
            return Results.success(result);
        } catch (Exception e) {
            log.error("聊天附件上传失败", e);
            return Results.success(Map.of("error", "上传失败: " + e.getMessage()));
        }
    }

    /**
     * 组装发给 LLM 的用户消息。全员可见附件；管理员额外标注入库能力。
     */
    private String buildUserMessageText(ChatRequest request, UserContext user) {
        String message = request.getMessage() != null ? request.getMessage().trim() : "";
        if (request.getAttachments() == null || request.getAttachments().isEmpty()) {
            return message;
        }
        StringBuilder sb = new StringBuilder(message);
        sb.append("\n\n[附件信息");
        if (user.isAdmin()) {
            sb.append(" — 解析用 read_chat_attachment；入库用 upload_knowledge_document");
        } else {
            sb.append(" — 解析/总结请用 read_chat_attachment");
        }
        sb.append("]\n");
        for (ChatAttachment attachment : request.getAttachments()) {
            sb.append("- 文件名: ").append(attachment.getName());
            if (attachment.getSize() != null) {
                sb.append(" (").append(attachment.getSize()).append(" bytes)");
            }
            if (attachment.getPath() != null && !attachment.getPath().isBlank()) {
                sb.append("\n  storage_path: ").append(attachment.getPath());
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    /**
     * 截断文本到指定长度
     *
     * @param text 原始文本
     * @param maxLen 最大长度
     * @return 截断后的文本
     */
    private String truncate(String text, int maxLen) {
        if (text == null) return null;
        return text.length() <= maxLen ? text : text.substring(0, maxLen);
    }

    /**
     * 对话请求 DTO
     *
     * <p>封装用户发送给 Agent 的对话请求参数。</p>
     */
    @lombok.Data
    public static class ChatRequest {
        /**
         * 会话 ID（可选）
         * <p>不传则创建新会话，传则继续该会话</p>
         */
        private Long sessionId;

        /**
         * 用户消息内容
         */
        private String message;

        /**
         * 是否启用联网搜索
         * <p>为 true 时 Agent 可使用 web_search 工具检索互联网内容</p>
         */
        private boolean webSearch;

        /**
         * 聊天附件（来自 /api/kb/agent/upload 的上传结果）
         */
        private List<ChatAttachment> attachments;
    }

    /**
     * 聊天附件元数据
     */
    @lombok.Data
    public static class ChatAttachment {
        /** 原始文件名 */
        private String name;
        /** 文件大小（字节） */
        private Long size;
        /** 存储路径（upload 接口返回的 path） */
        private String path;
    }
}
