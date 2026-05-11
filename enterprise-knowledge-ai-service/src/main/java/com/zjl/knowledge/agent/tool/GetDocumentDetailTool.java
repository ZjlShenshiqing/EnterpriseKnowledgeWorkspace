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
 * 文档详情 Tool
 */
@Component
@RequiredArgsConstructor
public class GetDocumentDetailTool implements McpTool {

    private final KbDocumentService kbDocumentService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("get_document_detail")
                .description("获取指定文档的详细信息，包括标题、摘要、类型、状态和上传时间。"
                        + "适用场景：用户询问'这个文档的详细信息是什么'、'查看文档ID为XX的内容'等")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .required(List.of("documentId"))
                        .properties(new LinkedHashMap<>() {{
                            put("documentId", ToolDefinition.PropertyDef.builder()
                                    .type("integer")
                                    .description("文档 ID")
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        Long documentId = getLong(args, "documentId");

        KbDocument doc = kbDocumentService.getVisible(documentId, user);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", doc.getId());
        result.put("title", doc.getTitle());
        result.put("status", doc.getStatus());
        result.put("fileType", doc.getFileType());
        result.put("fileName", doc.getFileName());
        result.put("fileSize", doc.getFileSize());
        result.put("summary", doc.getSummary());
        result.put("tags", doc.getTags());
        result.put("permissionType", doc.getPermissionType());
        result.put("chunkCount", doc.getChunkCount());
        result.put("createdAt", doc.getCreatedAt());
        result.put("updatedAt", doc.getUpdatedAt());

        return ToolResult.success(result);
    }

    private Long getLong(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v instanceof String s) {
            return Long.parseLong(s);
        }
        return null;
    }
}
