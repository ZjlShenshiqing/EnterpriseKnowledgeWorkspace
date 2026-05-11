package com.zjl.knowledge.agent.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * LLM 返回的 tool call
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {

    /** tool call 唯一 ID */
    private String id;

    /** 工具名 */
    private String name;

    /** 参数 */
    private Map<String, Object> arguments;
}
