package com.zjl.knowledge.agent.mcp;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * MCP Tool 定义，描述 Tool 的名称、描述和输入 Schema
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {

    /** 工具名（snake_case） */
    private String name;

    /** 给 LLM 看的描述 */
    private String description;

    /** 输入参数的 JSON Schema */
    private JsonSchema inputSchema;

    /**
     * 简化的 JSON Schema 定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JsonSchema {

        /** 顶层类型，固定为 "object" */
        @Builder.Default
        private String type = "object";

        /** 必填字段列表 */
        private List<String> required;

        /** 属性定义 */
        private Map<String, PropertyDef> properties;
    }

    /**
     * 单个属性的 Schema 定义
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PropertyDef {

        /** 属性类型：string / integer / number / boolean / array */
        private String type;

        /** 属性描述 */
        private String description;

        /** string 类型的枚举值 */
        private List<String> enumValues;

        /** 默认值 */
        private Object defaultValue;
    }
}
