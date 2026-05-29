package com.zjl.knowledge.agent.mcp.tool;

import com.zjl.knowledge.agent.mcp.McpTool;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.service.FileStorageService;
import com.zjl.knowledge.service.TikaDocumentParser;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 解析聊天附件 Tool（全员可用）：从 storage_path 读取并提取文档正文。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReadChatAttachmentTool implements McpTool {

    private static final int MAX_RETURN_CHARS = 12_000;

    private final FileStorageService fileStorageService;
    private final TikaDocumentParser tikaDocumentParser;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("read_chat_attachment")
                .description("读取并解析用户在对话中上传的附件内容（PDF/Word/Markdown 等）。"
                        + "当用户消息 [附件信息] 中含 storage_path，且需要总结、问答或分析该文件时使用。")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .required(List.of("storage_path"))
                        .properties(new LinkedHashMap<>() {{
                            put("storage_path", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("附件上传接口返回的 path，或用户消息 [附件信息] 中的 storage_path")
                                    .build());
                            put("file_name", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("原始文件名，可选，有助于解析")
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        String storagePath = stringArg(args, "storage_path");
        if (!StringUtils.hasText(storagePath)) {
            return ToolResult.failure("缺少 storage_path");
        }

        String fileName = stringArg(args, "file_name");
        if (!StringUtils.hasText(fileName)) {
            fileName = extractFileName(storagePath);
        }

        try {
            String text = tikaDocumentParser.extractText(
                    fileStorageService.read(storagePath),
                    fileName,
                    guessContentType(fileName));

            if (!StringUtils.hasText(text)) {
                return ToolResult.failure("未能从附件中提取文本，可能是不支持的格式或空文件");
            }

            boolean truncated = text.length() > MAX_RETURN_CHARS;
            String content = truncated ? text.substring(0, MAX_RETURN_CHARS) : text;

            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("fileName", fileName);
            payload.put("storagePath", storagePath);
            payload.put("charCount", text.length());
            payload.put("truncated", truncated);
            payload.put("content", content);
            return ToolResult.success(payload);
        } catch (Exception e) {
            log.warn("解析聊天附件失败: path={}", storagePath, e);
            return ToolResult.failure("解析附件失败: " + e.getMessage());
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
        return null;
    }

    private static String stringArg(Map<String, Object> args, String key) {
        Object v = args.get(key);
        return v != null ? String.valueOf(v).trim() : null;
    }
}
