package com.zjl.knowledge.agent.mcp;

import com.zjl.knowledge.web.UserContext;

/**
 * MCP Tool 接口，每个 Tool 实现此接口并注册到 ToolRegistry
 */
public interface McpTool {

    /**
     * 返回 Tool 的定义（name / description / inputSchema）
     *
     * @return ToolDefinition
     */
    ToolDefinition getDefinition();

    /**
     * 执行 Tool
     *
     * @param args 入参
     * @param user 当前用户上下文
     * @return ToolResult
     */
    ToolResult execute(java.util.Map<String, Object> args, UserContext user);
}
