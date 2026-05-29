package com.zjl.knowledge.agent.mcp.tool;

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
 *
 * <p>MCP 工具实现，用于获取指定文档的详细信息。</p>
 *
 * <p>工具名称：get_document_detail</p>
 * <p>适用场景：用户询问文档详细信息、查看指定文档内容等</p>
 *
 * @see McpTool
 * @see KbDocumentService
 */
@Component
@RequiredArgsConstructor
public class GetDocumentDetailTool implements McpTool {

    /**
     * 文档服务，提供文档的查询能力
     */
    private final KbDocumentService kbDocumentService;

    /**
     * 获取工具定义（Tool Schema）
     *
     * <p>定义工具的名称、描述和输入参数规范，供 LLM 理解和调用。</p>
     *
     * <p>输入参数：</p>
     * <ul>
     *   <li>documentId (integer, required) - 文档 ID</li>
     * </ul>
     *
     * @return 工具定义，包含名称、描述和 JSON Schema
     */
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

    /**
     * 执行工具调用
     *
     * <p>根据传入的 documentId 查询文档详情，并返回文档的完整信息。</p>
     *
     * <p>返回字段：</p>
     * <ul>
     *   <li>id - 文档 ID</li>
     *   <li>title - 文档标题</li>
     *   <li>status - 文档状态</li>
     *   <li>fileType - 文件类型</li>
     *   <li>fileName - 文件名</li>
     *   <li>fileSize - 文件大小</li>
     *   <li>summary - 文档摘要</li>
     *   <li>tags - 标签列表</li>
     *   <li>permissionType - 权限类型</li>
     *   <li>chunkCount - 分块数量</li>
     *   <li>createdAt - 创建时间</li>
     *   <li>updatedAt - 更新时间</li>
     * </ul>
     *
     * @param args 工具调用参数，包含 documentId
     * @param user  当前用户上下文，用于权限校验
     * @return 工具执行结果，包含文档详情
     */
    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        Long documentId = getLong(args, "documentId");

        // 根据文档 ID 和用户权限获取文档
        KbDocument doc = kbDocumentService.getVisible(documentId, user);

        // 构建返回结果
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

    /**
     * 从参数 Map 中安全获取 Long 类型值
     *
     * <p>支持 Number 类型和 String 类型的转换。</p>
     *
     * @param args 参数 Map
     * @param key  参数键名
     * @return Long 类型值，转换失败返回 null
     */
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
