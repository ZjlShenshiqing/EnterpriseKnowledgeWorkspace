package com.zjl.knowledge.agent.mcp.tool;

import com.zjl.knowledge.agent.mcp.McpTool;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.service.retrieval.RagRetrievalService;
import com.zjl.knowledge.service.retrieval.RetrievalResult;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * RAG 问答 Tool：委托 {@link RagRetrievalService} 执行检索，按文档聚合返回
 */
@Component
@RequiredArgsConstructor
public class RagQaTool implements McpTool {

    private final RagRetrievalService ragRetrievalService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("rag_qa")
                .description("基于知识库文档回答用户问题。将问题向量化后在 Milvus 中检索相关文档片段，"
                        + "返回最相关的文档及其匹配内容。"
                        + "适用场景：用户询问需要从文档中查找答案的问题，如'差旅报销需要什么材料'、'微服务架构的最佳实践是什么'")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .required(List.of("question"))
                        .properties(new LinkedHashMap<>() {{
                            put("question", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("用户的问题")
                                    .build());
                            put("topK", ToolDefinition.PropertyDef.builder()
                                    .type("integer")
                                    .description("返回的文档数量，默认5，最大10")
                                    .defaultValue(5)
                                    .build());
                            put("kbId", ToolDefinition.PropertyDef.builder()
                                    .type("integer")
                                    .description("知识库 ID，不传时检索所有知识库")
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        String question = (String) args.get("question");
        int topK = getInt(args, "topK", 5);
        Long kbId = getLong(args, "kbId");

        RetrievalResult result = ragRetrievalService.retrieve(question, topK, user, kbId);

        List<Map<String, Object>> documents = new ArrayList<>();
        for (RetrievalResult.DocumentResult doc : result.documents()) {
            List<Map<String, Object>> matchedChunks = new ArrayList<>();
            for (RetrievalResult.ChunkResult chunk : doc.matchedChunks()) {
                Map<String, Object> chunkInfo = new LinkedHashMap<>();
                chunkInfo.put("chunkIndex", chunk.chunkIndex());
                chunkInfo.put("text", chunk.text());
                chunkInfo.put("score", chunk.score());
                chunkInfo.put("metadata", chunk.metadata());
                chunkInfo.put("rerankScore", chunk.rerankScore());
                chunkInfo.put("rerankStrategy", chunk.rerankStrategy());
                chunkInfo.put("rerankReason", chunk.rerankReason());
                matchedChunks.add(chunkInfo);
            }
            Map<String, Object> docInfo = new LinkedHashMap<>();
            docInfo.put("documentId", doc.documentId());
            docInfo.put("title", doc.title());
            docInfo.put("summary", doc.summary());
            docInfo.put("fileType", doc.fileType());
            docInfo.put("fileName", doc.fileName());
            docInfo.put("fileSize", doc.fileSize());
            docInfo.put("createdAt", doc.createdAt());
            docInfo.put("metadata", doc.metadata());
            docInfo.put("matchedChunks", matchedChunks);
            documents.add(docInfo);
        }

        return ToolResult.success(Map.of("documents", documents));
    }

    private int getInt(Map<String, Object> args, String key, int defaultValue) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return Math.min(n.intValue(), 10);
        }
        return defaultValue;
    }

    private Long getLong(Map<String, Object> args, String key) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return null;
    }
}
