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
 * 创建会议 Tool。
 */
@Component
@RequiredArgsConstructor
public class CreateMeetingTool implements McpTool {

    private final CollaborationClient collaborationClient;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("create_meeting")
                .description("为用户创建会议预约。线下会议创建前建议先调用 check_meeting_conflict。"
                        + "适用场景：用户说'帮我约个会'、'预约明天10点的会议'等")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .required(List.of("title", "date", "start_time", "room"))
                        .properties(new LinkedHashMap<>() {{
                            put("title", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("会议标题")
                                    .build());
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
                                    .description("结束时间，格式 HH:mm；与 duration_minutes 二选一")
                                    .build());
                            put("duration_minutes", ToolDefinition.PropertyDef.builder()
                                    .type("integer")
                                    .description("会议时长（分钟），默认 60；未提供 end_time 时使用")
                                    .defaultValue(60)
                                    .build());
                            put("room", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("会议室：A301 (20人) / B102 (10人) / C501 (50人) / 线上-Zoom")
                                    .build());
                            put("attendees", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("参会人，逗号分隔，可选")
                                    .build());
                            put("description", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("会议备注/议程，可选")
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        String title = requiredString(args, "title");
        String date = requiredString(args, "date");
        String startTime = requiredString(args, "start_time");
        String room = requiredString(args, "room");
        if (title == null || date == null || startTime == null || room == null) {
            return ToolResult.failure("缺少必填参数：title、date、start_time、room");
        }

        String endTime = optionalString(args, "end_time");
        if (endTime == null || endTime.isBlank()) {
            int duration = intArg(args, "duration_minutes", 60);
            endTime = calcEndTime(startTime, duration);
        }

        try {
            if (!"线上-Zoom".equals(room)) {
                Map<String, Object> checkBody = new LinkedHashMap<>();
                checkBody.put("date", date);
                checkBody.put("startTime", startTime);
                checkBody.put("endTime", endTime);
                checkBody.put("room", room);
                Result<Map<String, Object>> conflictResult = collaborationClient.checkMeetingConflict(user, checkBody);
                if (Result.SUCCESS_CODE.equals(conflictResult.getCode()) && conflictResult.getData() != null) {
                    Object conflictFlag = conflictResult.getData().get("conflict");
                    if (Boolean.TRUE.equals(conflictFlag)) {
                        return ToolResult.failure("该时段会议室已被占用: "
                                + conflictResult.getData().getOrDefault("message", "存在冲突"));
                    }
                }
            }

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("title", title);
            body.put("date", date);
            body.put("startTime", startTime);
            body.put("endTime", endTime);
            body.put("room", room);
            String attendees = optionalString(args, "attendees");
            if (attendees != null) {
                body.put("attendees", attendees);
            }
            String description = optionalString(args, "description");
            if (description != null) {
                body.put("description", description);
            }

            Result<Long> result = collaborationClient.createMeeting(user, body);
            if (!Result.SUCCESS_CODE.equals(result.getCode())) {
                return ToolResult.failure(result.getMessage() != null ? result.getMessage() : "创建会议失败");
            }

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("meetingId", result.getData());
            payload.put("title", title);
            payload.put("date", date);
            payload.put("startTime", startTime);
            payload.put("endTime", endTime);
            payload.put("room", room);
            payload.put("message", "会议创建成功");
            return ToolResult.success(payload);
        } catch (Exception e) {
            return ToolResult.failure("创建会议失败: " + e.getMessage());
        }
    }

    private static String calcEndTime(String startTime, int durationMinutes) {
        int start = toMinutes(startTime);
        int end = start + durationMinutes;
        int hour = end / 60;
        int minute = end % 60;
        return String.format("%02d:%02d", hour, minute);
    }

    private static int toMinutes(String time) {
        String[] parts = time.split(":");
        int hour = Integer.parseInt(parts[0]);
        int minute = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
        return hour * 60 + minute;
    }

    private static String requiredString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return String.valueOf(v).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v != null ? String.valueOf(v).trim() : null;
    }

    private static int intArg(Map<String, Object> args, String key, int defaultValue) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }
}
