package com.zjl.knowledge.agent.mcp.tool;

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
 * 取消会议 Tool。
 */
@Component
@RequiredArgsConstructor
public class CancelMeetingTool implements McpTool {

    private final CollaborationClient collaborationClient;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("cancel_meeting")
                .description("取消已预约的会议。仅适用于用户有权限取消的会议。"
                        + "适用场景：用户说'取消刚才的会议'、'删掉明天那个会'等")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .required(List.of("meeting_id"))
                        .properties(new LinkedHashMap<>() {{
                            put("meeting_id", ToolDefinition.PropertyDef.builder()
                                    .type("integer")
                                    .description("会议 ID，可通过 list_my_meetings 获取")
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        Long meetingId = longArg(args, "meeting_id");
        if (meetingId == null) {
            return ToolResult.failure("缺少必填参数 meeting_id");
        }

        try {
            Result<Void> result = collaborationClient.cancelMeeting(user, meetingId);
            if (!Result.SUCCESS_CODE.equals(result.getCode())) {
                return ToolResult.failure(result.getMessage() != null ? result.getMessage() : "取消会议失败");
            }
            return ToolResult.success(Map.of("meetingId", meetingId, "message", "会议已取消"));
        } catch (Exception e) {
            return ToolResult.failure("取消会议失败: " + e.getMessage());
        }
    }

    private static Long longArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v != null) {
            try {
                return Long.parseLong(String.valueOf(v));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
