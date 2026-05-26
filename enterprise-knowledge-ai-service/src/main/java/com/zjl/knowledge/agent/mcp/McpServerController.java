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
 * MCP Server 端点
 *
 * <p>实现 Model Context Protocol (MCP) 协议的 HTTP/SSE 传输模式。
 * MCP 是 Anthropic 提出的开放协议，用于标准化 LLM 与外部工具/资源的交互。</p>
 *
 * <p>协议端点：</p>
 * <ul>
 *   <li><strong>GET /mcp/sse</strong> - 建立 SSE 连接，获取会话 ID</li>
 *   <li><strong>POST /mcp/tools/list</strong> - 工具发现，返回所有可用工具定义</li>
 *   <li><strong>POST /mcp/messages</strong> - 执行工具调用</li>
 * </ul>
 *
 * <p>典型调用流程：</p>
 * <ol>
 *   <li>客户端连接 SSE 端点获取 sessionId</li>
 *   <li>客户端调用 /tools/list 发现可用工具</li>
 *   <li>客户端通过 /messages 调用具体工具</li>
 * </ol>
 *
 * @see ToolRegistry
 * @see <a href="https://modelcontextprotocol.io/">MCP 协议规范</a>
 */
@Slf4j
@RestController
@RequestMapping("/mcp")
@RequiredArgsConstructor
public class McpServerController {

    /**
     * 工具注册中心，管理所有可用的 MCP 工具
     */
    private final ToolRegistry toolRegistry;

    /**
     * 建立 MCP SSE 连接
     *
     * <p>客户端首先调用此端点建立长连接，服务端返回唯一的 sessionId。
     * 连接有效期为 5 分钟（300秒），超时自动关闭。</p>
     *
     * <p>SSE 事件说明：</p>
     * <ul>
     *   <li>event: endpoint - 包含消息端点地址，客户端后续通过此地址发送工具调用</li>
     * </ul>
     *
     * @return SSE 流，返回 sessionId 和消息端点地址
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
     * 工具发现：返回所有注册的 Tool 定义（JSON Schema）
     *
     * <p>客户端通过此端点获取服务端支持的所有工具列表及其参数定义。
     * 返回格式符合 MCP 协议规范，包含工具名称、描述和输入参数 Schema。</p>
     *
     * <p>响应示例：</p>
     * <pre>
     * {
     *   "tools": [
     *     {
     *       "name": "search_documents",
     *       "description": "搜索文档",
     *       "inputSchema": {
     *         "type": "object",
     *         "properties": {
     *           "query": {"type": "string"}
     *         },
     *         "required": ["query"]
     *       }
     *     }
     *   ]
     * }
     * </pre>
     *
     * @return 工具定义列表
     */
    @PostMapping("/tools/list")
    public Result<Map<String, Object>> listTools() {
        UserContext user = UserContextHolder.get();
        List<ToolDefinition> tools = toolRegistry.getDefinitionsFor(user);

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
     * 接收 Tool 调用请求
     *
     * <p>客户端通过此端点执行具体的工具调用。服务端根据工具名查找对应的工具实现，
     * 执行后返回结果。工具执行时会携带当前用户上下文，用于权限校验和审计。</p>
     *
     * <p>请求示例：</p>
     * <pre>
     * POST /mcp/messages?sessionId=xxx
     * {
     *   "name": "search_documents",
     *   "arguments": {
     *     "query": "Spring Boot"
     *   }
     * }
     * </pre>
     *
     * @param sessionId MCP SSE 会话 ID，用于关联调用上下文
     * @param request   Tool 调用请求，包含工具名和参数
     * @return 工具执行结果
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
     * MCP Tool 调用请求 DTO
     *
     * <p>封装工具调用的请求参数，符合 MCP 协议的消息格式。</p>
     */
    @lombok.Data
    public static class ToolCallRequest {
        /** 工具名称，对应 ToolRegistry 中注册的工具名 */
        private String name;
        /** 工具调用参数，JSON 对象格式，需符合工具的 inputSchema 定义 */
        private Map<String, Object> arguments;
    }
}
