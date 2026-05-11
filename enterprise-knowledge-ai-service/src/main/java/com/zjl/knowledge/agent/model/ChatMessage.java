package com.zjl.knowledge.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Agent 对话消息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {

    /** 消息角色：system / user / assistant / tool */
    private String role;

    /** 文本内容 */
    private String content;

    /** assistant 消息中的 tool call 列表 */
    private List<ToolCall> toolCalls;

    /** tool 消息的回填 ID */
    private String toolCallId;

    /** tool 消息的工具名 */
    private String toolName;
}
