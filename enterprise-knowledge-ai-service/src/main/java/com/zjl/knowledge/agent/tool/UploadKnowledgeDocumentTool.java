package com.zjl.knowledge.agent.tool;

import com.zjl.knowledge.agent.mcp.McpTool;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.dto.KbDocumentUploadRequest;
import com.zjl.knowledge.service.FileStorageService;
import com.zjl.knowledge.service.KbDocumentService;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 上传知识库文档 Tool（仅管理员）。
 *
 * <p>文件需先通过 {@code POST /api/kb/agent/upload} 上传，再将返回的 storage_path 传入本工具。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UploadKnowledgeDocumentTool implements McpTool {

    private final KbDocumentService kbDocumentService;
    private final FileStorageService fileStorageService;

    @Override
    public boolean isAllowed(UserContext user) {
        return user != null && user.isAdmin();
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("upload_knowledge_document")
                .description("将已上传的附件导入知识库并触发分块。仅管理员可用。"
                        + "使用前请让用户在对话中上传文件（/api/kb/agent/upload），"
                        + "并用 list_knowledge_bases 选择 kb_id、用 list_document_categories 选择 category_id。"
                        + "permission_type 可选：ALL / DEPARTMENT / PROJECT / USER / ADMIN，默认 ALL。")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .required(List.of("storage_path", "title", "category_id"))
                        .properties(new LinkedHashMap<>() {{
                            put("storage_path", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("聊天附件上传接口返回的 path 字段")
                                    .build());
                            put("file_name", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("原始文件名，可选")
                                    .build());
                            put("title", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("文档标题")
                                    .build());
                            put("category_id", ToolDefinition.PropertyDef.builder()
                                    .type("integer")
                                    .description("文档分类 ID，可通过 list_document_categories 获取")
                                    .build());
                            put("kb_id", ToolDefinition.PropertyDef.builder()
                                    .type("integer")
                                    .description("目标知识库 ID，通过 list_knowledge_bases 获取")
                                    .build());
                            put("permission_type", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("权限类型，默认 ALL")
                                    .defaultValue("ALL")
                                    .build());
                            put("tags", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("标签，逗号分隔，可选")
                                    .build());
                            put("chunk_strategy", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("分块策略：FIXED_SIZE 或 PARAGRAPH，默认 FIXED_SIZE")
                                    .defaultValue("FIXED_SIZE")
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        String storagePath = requiredString(args, "storage_path");
        String title = requiredString(args, "title");
        Long categoryId = longArg(args, "category_id");
        if (storagePath == null || title == null || categoryId == null) {
            return ToolResult.failure("缺少必填参数：storage_path、title、category_id");
        }

        String fileName = optionalString(args, "file_name");
        if (!StringUtils.hasText(fileName)) {
            fileName = extractFileName(storagePath);
        }

        try {
            byte[] bytes = fileStorageService.read(storagePath).readAllBytes();
            if (bytes.length == 0) {
                return ToolResult.failure("文件为空或读取失败");
            }

            KbDocumentUploadRequest meta = new KbDocumentUploadRequest();
            meta.setTitle(title);
            meta.setCategoryId(categoryId);
            meta.setKbId(longArg(args, "kb_id"));
            meta.setPermissionType(optionalString(args, "permission_type", "ALL"));
            meta.setTags(optionalString(args, "tags"));
            meta.setChunkStrategy(optionalString(args, "chunk_strategy", "FIXED_SIZE"));
            meta.setSourceType("FILE");

            SimpleMultipartFile file = new SimpleMultipartFile(
                    fileName,
                    guessContentType(fileName),
                    bytes);

            Long documentId = kbDocumentService.upload(user, meta, file);
            kbDocumentService.startChunk(documentId, user);

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("documentId", documentId);
            payload.put("title", title);
            payload.put("status", "PENDING/RUNNING");
            payload.put("message", "文档已上传并开始分块，请稍后通过 list_documents 或 search_documents 查看");
            return ToolResult.success(payload);
        } catch (Exception e) {
            log.error("管理员上传知识库文档失败: path={}", storagePath, e);
            return ToolResult.failure("上传失败: " + e.getMessage());
        }
    }

    private static String extractFileName(String storagePath) {
        int idx = storagePath.lastIndexOf('/');
        return idx >= 0 ? storagePath.substring(idx + 1) : storagePath;
    }

    private static String guessContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) {
            return "application/pdf";
        }
        if (lower.endsWith(".docx")) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        if (lower.endsWith(".doc")) {
            return "application/msword";
        }
        if (lower.endsWith(".txt")) {
            return "text/plain";
        }
        if (lower.endsWith(".md")) {
            return "text/markdown";
        }
        if (lower.endsWith(".html")) {
            return "text/html";
        }
        return "application/octet-stream";
    }

    private static String requiredString(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return null;
        }
        return String.valueOf(v).trim();
    }

    private static String optionalString(Map<String, Object> args, String key) {
        return optionalString(args, key, null);
    }

    private static String optionalString(Map<String, Object> args, String key, String defaultValue) {
        Object v = args.get(key);
        if (v == null || String.valueOf(v).isBlank()) {
            return defaultValue;
        }
        return String.valueOf(v).trim();
    }

    private static Long longArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        if (v != null) {
            try {
                return Long.parseLong(String.valueOf(v));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}
