package com.zjl.knowledge.agent.mcp.tool;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.zjl.knowledge.agent.mcp.McpTool;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.domain.DocumentStatus;
import com.zjl.knowledge.entity.KbDocument;
import com.zjl.knowledge.entity.KbDocumentChunk;
import com.zjl.knowledge.entity.KbDocumentPermission;
import com.zjl.knowledge.mapper.KbDocumentChunkMapper;
import com.zjl.knowledge.mapper.KbDocumentMapper;
import com.zjl.knowledge.mapper.KbDocumentPermissionMapper;
import com.zjl.knowledge.milvus.SearchResult;
import com.zjl.knowledge.service.DocumentVisibilityService;
import com.zjl.knowledge.service.VectorSyncService;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * RAG 问答 Tool：向量检索 → 按文档聚合 → 返回 top-K 文档及匹配片段
 */
@Component
@RequiredArgsConstructor
public class RagQaTool implements McpTool {

    private final VectorSyncService vectorSyncService;
    private final KbDocumentMapper kbDocumentMapper;
    private final KbDocumentChunkMapper kbDocumentChunkMapper;
    private final KbDocumentPermissionMapper kbDocumentPermissionMapper;
    private final DocumentVisibilityService documentVisibilityService;

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
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        String question = (String) args.get("question");
        int topK = getInt(args, "topK", 5);

        KbDocument contextDoc = new KbDocument();
        List<SearchResult> searchResults = vectorSyncService.searchSimilar(question, topK * 3, contextDoc);

        if (searchResults.isEmpty()) {
            return ToolResult.success(Map.of("documents", List.of()));
        }

        List<Long> docIds = searchResults.stream()
                .map(r -> Long.parseLong(r.docId()))
                .distinct()
                .collect(Collectors.toList());

        Map<Long, KbDocument> docMap = kbDocumentMapper.selectBatchIds(docIds).stream()
                .filter(d -> d.getDeleted() == null || d.getDeleted() == 0)
                .filter(this::isSearchable)
                .filter(d -> isVisible(d, user))
                .collect(Collectors.toMap(KbDocument::getId, d -> d, (left, right) -> left, LinkedHashMap::new));

        Map<Long, List<SearchResult>> resultsByDoc = searchResults.stream()
                .filter(r -> docMap.containsKey(Long.parseLong(r.docId())))
                .collect(Collectors.groupingBy(r -> Long.parseLong(r.docId())));

        List<Map<String, Object>> documents = new ArrayList<>();
        int count = 0;
        for (Long docId : docIds) {
            if (count >= topK) break;
            KbDocument doc = docMap.get(docId);
            if (doc == null) continue;
            List<SearchResult> docResults = resultsByDoc.get(doc.getId());
            if (docResults == null || docResults.isEmpty()) continue;

            List<Map<String, Object>> matchedChunks = buildMatchedChunks(doc.getId(), docResults);
            if (matchedChunks.isEmpty()) continue;

            Map<String, Object> docInfo = new LinkedHashMap<>();
            docInfo.put("documentId", doc.getId());
            docInfo.put("title", doc.getTitle());
            docInfo.put("summary", doc.getSummary());
            docInfo.put("fileType", doc.getFileType());
            docInfo.put("fileName", doc.getFileName());
            docInfo.put("fileSize", doc.getFileSize());
            docInfo.put("createdAt", doc.getCreatedAt());
            docInfo.put("metadata", parseJson(doc.getMetadata()));
            docInfo.put("matchedChunks", matchedChunks);
            documents.add(docInfo);
            count++;
        }

        return ToolResult.success(Map.of("documents", documents));
    }

    private List<Map<String, Object>> buildMatchedChunks(Long docId, List<SearchResult> results) {
        List<Long> chunkIds = results.stream()
                .map(r -> Long.parseLong(r.chunkId()))
                .collect(Collectors.toList());

        Map<Long, KbDocumentChunk> chunkMap = kbDocumentChunkMapper.selectBatchIds(chunkIds).stream()
                .collect(Collectors.toMap(KbDocumentChunk::getId, c -> c, (left, right) -> left));

        Map<Long, Float> scoreMap = new LinkedHashMap<>();
        Map<Long, Map<String, Object>> metaMap = new LinkedHashMap<>();
        for (SearchResult r : results) {
            scoreMap.put(Long.parseLong(r.chunkId()), r.score());
            metaMap.put(Long.parseLong(r.chunkId()), r.metadata());
        }

        return chunkIds.stream()
                .map(chunkMap::get)
                .filter(c -> c != null)
                .filter(c -> c.getEnabled() == null || c.getEnabled() == 1)
                .map(c -> {
                    Map<String, Object> chunkInfo = new LinkedHashMap<>();
                    chunkInfo.put("chunkIndex", c.getChunkIndex());
                    chunkInfo.put("text", c.getChunkText());
                    chunkInfo.put("score", scoreMap.getOrDefault(c.getId(), 0f));
                    chunkInfo.put("metadata", metaMap.getOrDefault(c.getId(), Map.of()));
                    return chunkInfo;
                })
                .collect(Collectors.toList());
    }

    private boolean isSearchable(KbDocument doc) {
        boolean enabled = doc.getEnabled() == null || doc.getEnabled() == 1;
        return enabled && DocumentStatus.SUCCESS.name().equals(doc.getStatus());
    }

    private boolean isVisible(KbDocument doc, UserContext user) {
        List<KbDocumentPermission> permissions = kbDocumentPermissionMapper.selectList(
                new LambdaQueryWrapper<KbDocumentPermission>()
                        .eq(KbDocumentPermission::getDocumentId, doc.getId())
        );
        return documentVisibilityService.canView(doc, user, permissions);
    }

    private int getInt(Map<String, Object> args, String key, int defaultValue) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return Math.min(n.intValue(), 10);
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Object parseJson(String json) {
        if (json == null || json.isEmpty()) return Map.of();
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().readValue(json, Map.class);
        } catch (Exception e) {
            return json;
        }
    }
}
