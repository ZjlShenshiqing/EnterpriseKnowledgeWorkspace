package com.zjl.knowledge.agent.tool;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zjl.knowledge.agent.mcp.McpTool;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.service.KbDocumentService;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档列表 Tool
 */
@Component
@RequiredArgsConstructor
public class ListDocumentsTool implements McpTool {

    private final KbDocumentService kbDocumentService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("list_documents")
                .description("分页查询当前用户可见的文档列表。"
                        + "适用场景：用户询问'最近有哪些文档'、'查看文档列表'等")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .properties(new LinkedHashMap<>() {{
                            put("current", ToolDefinition.PropertyDef.builder()
                                    .type("integer")
                                    .description("页码，从1开始")
                                    .defaultValue(1)
                                    .build());
                            put("size", ToolDefinition.PropertyDef.builder()
                                    .type("integer")
                                    .description("每页条数，默认20")
                                    .defaultValue(20)
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        long current = getLong(args, "current", 1);
        long size = Math.min(getLong(args, "size", 20), 50);

        Page<KbDocument> page = new Page<>(current, size);
        IPage<KbDocument> pageResult = kbDocumentService.pageVisible(page, user);

        List<Map<String, Object>> records = pageResult.getRecords().stream()
                .map(doc -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", doc.getId());
                    m.put("title", doc.getTitle());
                    m.put("status", doc.getStatus());
                    m.put("fileType", doc.getFileType());
                    m.put("summary", doc.getSummary());
                    m.put("createdAt", doc.getCreatedAt());
                    return m;
                })
                .toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());
        result.put("total", pageResult.getTotal());
        result.put("records", records);

        return ToolResult.success(result);
    }

    private long getLong(Map<String, Object> args, String key, long defaultValue) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return defaultValue;
    }
}
