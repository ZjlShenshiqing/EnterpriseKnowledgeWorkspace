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
 * 检测会议室时间冲突 Tool。
 */
@Component
@RequiredArgsConstructor
public class CheckMeetingConflictTool implements McpTool {

    private final CollaborationClient collaborationClient;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("check_meeting_conflict")
                .description("检测指定日期、时间段、会议室是否已被占用。"
                        + "创建线下会议前应优先调用此工具。"
                        + "线下会议室：A301 (20人)、B102 (10人)、C501 (50人)；线上会议选 线上-Zoom（不检测占用）")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .required(List.of("date", "start_time", "end_time", "room"))
                        .properties(new LinkedHashMap<>() {{
                            put("date", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("会议日期，格式 YYYY-MM-DD")
                                    .build());
                            put("start_time", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("开始时间，格式 HH:mm")
                                    .build());
                            put("end_time", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("结束时间，格式 HH:mm")
                                    .build());
                            put("room", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("会议室：A301 (20人) / B102 (10人) / C501 (50人) / 线上-Zoom")
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        String date = requiredString(args, "date");
        String startTime = requiredString(args, "start_time");
        String endTime = requiredString(args, "end_time");
        String room = requiredString(args, "room");
        if (date == null || startTime == null || endTime == null || room == null) {
            return ToolResult.failure("缺少必填参数：date、start_time、end_time、room");
        }

        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("date", date);
            body.put("startTime", startTime);
            body.put("endTime", endTime);
            body.put("room", room);

            Result<Map<String, Object>> result = collaborationClient.checkMeetingConflict(user, body);
            if (!Result.SUCCESS_CODE.equals(result.getCode())) {
                return ToolResult.failure(result.getMessage() != null ? result.getMessage() : "冲突检测失败");
            }
            return ToolResult.success(result.getData() != null ? result.getData() : Map.of());
        } catch (Exception e) {
            return ToolResult.failure("冲突检测失败: " + e.getMessage());
        }
    }

    private static String requiredString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return String.valueOf(v).trim();
    }
}
