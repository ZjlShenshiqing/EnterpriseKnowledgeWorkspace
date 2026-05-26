package com.zjl.knowledge.agent.mcp;

import com.zjl.common.enums.ErrorCode;
import com.zjl.common.exception.BizException;
import com.zjl.knowledge.web.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tool 注册表：收集所有 {@link McpTool} 实现，按名称索引
 */
@Slf4j
@Component
public class ToolRegistry {

    private final Map<String, McpTool> tools = new LinkedHashMap<>();

    /**
     * 构造器注入所有 McpTool Bean
     *
     * @param toolList Spring 容器中所有 McpTool 实现
     */
    public ToolRegistry(List<McpTool> toolList) {
        for (McpTool tool : toolList) {
            tools.put(tool.getDefinition().getName(), tool);
        }
    }

    /**
     * 获取所有 Tool 定义（供 LLM 和 MCP /tools/list 使用）
     *
     * @return ToolDefinition 列表
     */
    public List<ToolDefinition> getAllDefinitions() {
        return tools.values().stream()
                .map(McpTool::getDefinition)
                .toList();
    }

    /**
     * 获取当前用户可用的 Tool 定义（按 {@link McpTool#isAllowed} 过滤）。
     *
     * @param user 当前用户
     * @return ToolDefinition 列表
     */
    public List<ToolDefinition> getDefinitionsFor(UserContext user) {
        return tools.values().stream()
                .filter(tool -> tool.isAllowed(user))
                .map(McpTool::getDefinition)
                .toList();
    }

    /**
     * 按名称获取 Tool
     *
     * @param name 工具名
     * @return McpTool
     * @throws BizException 工具不存在时抛出
     */
    public McpTool requireTool(String name) {
        McpTool tool = tools.get(name);
        if (tool == null) {
            throw new BizException(ErrorCode.PARAM_INVALID, "工具不存在: " + name);
        }
        return tool;
    }

    /**
     * 执行指定 Tool
     *
     * @param name 工具名
     * @param args 入参
     * @param user 当前用户
     * @return ToolResult
     */
    public ToolResult execute(String name, Map<String, Object> args, UserContext user) {
        try {
            McpTool tool = requireTool(name);
            if (!tool.isAllowed(user)) {
                return ToolResult.failure("权限不足：仅管理员可使用工具 " + name);
            }
            return tool.execute(args, user);
        } catch (BizException e) {
            log.warn("工具执行失败: name={}, error={}", name, e.getMessage());
            return ToolResult.failure(e.getMessage());
        } catch (Exception e) {
            log.error("工具执行异常: name={}", name, e);
            return ToolResult.failure("工具执行异常: " + e.getMessage());
        }
    }
}
