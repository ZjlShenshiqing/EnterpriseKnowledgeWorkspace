package com.zjl.knowledge.agent.mcp;

import com.zjl.knowledge.web.UserContext;

/**
 * MCP Tool 接口，每个 Tool 实现此接口并注册到 ToolRegistry
 */
public interface McpTool {

    /**
     * 当前用户是否可使用该 Tool（默认全部用户可用，管理类 Tool 可覆写）。
     *
     * @param user 当前用户
     * @return 是否允许
     */
    default boolean isAllowed(UserContext user) {
        return true;
    }

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
