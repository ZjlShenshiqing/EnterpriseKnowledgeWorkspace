package com.zjl.knowledge.agent.tool;

import com.zjl.common.response.Result;
import com.zjl.knowledge.agent.integration.CollaborationClient;
import com.zjl.knowledge.agent.mcp.McpTool;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 查询当前用户会议列表 Tool。
 */
@Component
@RequiredArgsConstructor
public class ListMyMeetingsTool implements McpTool {

    private final CollaborationClient collaborationClient;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("list_my_meetings")
                .description("查询当前用户相关的会议列表（我创建的或我是参会人的）。"
                        + "适用场景：用户询问'我有哪些会议'、'今天的安排'、'查看我的会议'等")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .properties(new LinkedHashMap<>() {{
                            put("date", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("可选，按日期过滤，格式 YYYY-MM-DD")
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        try {
            Result<List<Map<String, Object>>> result = collaborationClient.listMyMeetings(user);
            if (!Result.SUCCESS_CODE.equals(result.getCode())) {
                return ToolResult.failure(result.getMessage() != null ? result.getMessage() : "查询会议失败");
            }
            List<Map<String, Object>> meetings = result.getData() != null ? result.getData() : List.of();
            String dateFilter = stringArg(args, "date");
            if (dateFilter != null && !dateFilter.isBlank()) {
                meetings = meetings.stream()
                        .filter(m -> dateFilter.equals(String.valueOf(m.get("date"))))
                        .toList();
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("total", meetings.size());
            payload.put("meetings", meetings);
            return ToolResult.success(payload);
        } catch (Exception e) {
            return ToolResult.failure("查询会议失败: " + e.getMessage());
        }
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v != null ? String.valueOf(v) : null;
    }
}
