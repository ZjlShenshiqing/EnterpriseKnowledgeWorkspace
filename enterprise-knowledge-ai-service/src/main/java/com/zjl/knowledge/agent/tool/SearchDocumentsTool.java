package com.zjl.knowledge.agent.tool;

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
 * 搜索文档 Tool。
 */
@Component
@RequiredArgsConstructor
public class SearchDocumentsTool implements McpTool {

    private final KbDocumentService kbDocumentService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("search_documents")
                .description("搜索知识库中的文档。根据关键词在文档标题中进行模糊匹配。"
                        + "仅返回当前用户有权限查看的文档。"
                        + "适用场景：用户询问'找XX文档'、'有没有关于XX的资料'等")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .required(List.of("keyword"))
                        .properties(new LinkedHashMap<>() {{
                            put("keyword", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("搜索关键词，在文档标题中模糊匹配")
                                    .build());
                            put("limit", ToolDefinition.PropertyDef.builder()
                                    .type("integer")
                                    .description("返回数量上限，默认10，最大50")
                                    .defaultValue(10)
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        String keyword = (String) args.get("keyword");
        int limit = getInt(args, "limit", 10);

        List<KbDocument> docs = kbDocumentService.searchDocuments(user, keyword, limit);

        List<Map<String, Object>> results = docs.stream()
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

        return ToolResult.success(results);
    }

    private int getInt(Map<String, Object> args, String key, int defaultValue) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return Math.min(n.intValue(), 50);
        }
        return defaultValue;
    }
}
