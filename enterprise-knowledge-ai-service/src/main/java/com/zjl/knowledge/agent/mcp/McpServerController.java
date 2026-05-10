package com.zjl.knowledge.agent.mcp;

import com.zjl.common.response.Result;
import com.zjl.common.response.Results;
import com.zjl.knowledge.web.UserContext;
import com.zjl.knowledge.web.UserContextHolder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * MCP Server 端点。
 *
 * <p>实现 MCP 协议的 HTTP/SSE 传输模式：
 * GET  /mcp/sse       → 建立 SSE 连接
 * POST /mcp/tools/list → 工具发现
 * POST /mcp/messages   → Tool 调用
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpServerController {

    private final ToolRegistry toolRegistry;

    /**
     * 建立 MCP SSE 连接。
     *
     * @return SSE 流，返回 sessionId
     */
    @GetMapping(value = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect() {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(300_000L);

        // 发送 endpoint 事件，告知客户端消息端点地址
        try {
            emitter.send(SseEmitter.event()
                    .name("endpoint")
                    .data("/mcp/messages?sessionId=" + sessionId));
        } catch (Exception e) {
            log.warn("MCP SSE 发送失败", e);
            emitter.completeWithError(e);
        }

        emitter.onCompletion(() -> log.debug("MCP SSE 连接关闭, sessionId={}", sessionId));
        emitter.onTimeout(() -> log.debug("MCP SSE 连接超时, sessionId={}", sessionId));

        return emitter;
    }

    /**
     * 工具发现：返回所有注册的 Tool 定义（JSON Schema）。
     */
    @PostMapping("/tools/list")
    public Result<Map<String, Object>> listTools() {
        List<ToolDefinition> tools = toolRegistry.getAllDefinitions();

        return Results.success(Map.of(
                "tools", tools.stream()
                        .map(t -> Map.of(
                                "name", t.getName(),
                                "description", t.getDescription(),
                                "inputSchema", t.getInputSchema()
                        ))
                        .toList()
        ));
    }

    /**
     * 接收 Tool 调用请求。
     *
     * @param sessionId MCP SSE 会话 ID
     * @param request   Tool 调用请求
     * @return ToolResult
     */
    @PostMapping("/messages")
    public Result<ToolResult> handleMessage(
            @RequestParam String sessionId,
            @RequestBody ToolCallRequest request) {

        UserContext user = UserContextHolder.get();
        log.info("MCP tool call: sessionId={}, tool={}", sessionId, request.getName());

        ToolResult result = toolRegistry.execute(
                request.getName(), request.getArguments(), user);

        return Results.success(result);
    }

    /**
     * MCP Tool 调用请求。
     */
    @lombok.Data
    public static class ToolCallRequest {
        /** 工具名 */
        private String name;
        /** 工具参数 */
        private Map<String, Object> arguments;
    }
}
