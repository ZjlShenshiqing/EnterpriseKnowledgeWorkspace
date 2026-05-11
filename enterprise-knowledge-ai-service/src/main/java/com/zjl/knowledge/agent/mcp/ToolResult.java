package com.zjl.knowledge.agent.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Tool 执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    /** 是否成功 */
    private boolean success;

    /** 结果数据（JSON 可序列化） */
    private Object data;

    /** 错误信息（失败时） */
    private String error;

    /**
     * 创建成功结果
     *
     * @param data 结果数据
     * @return ToolResult
     */
    public static ToolResult success(Object data) {
        return ToolResult.builder().success(true).data(data).build();
    }

    /**
     * 创建失败结果
     *
     * @param error 错误信息
     * @return ToolResult
     */
    public static ToolResult failure(String error) {
        return ToolResult.builder().success(false).error(error).build();
    }
}
