package com.zjl.knowledge.agent.mcp.tool;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.zjl.knowledge.agent.mcp.McpTool;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.dto.kb.KbKnowledgeBasePageRequest;
import com.zjl.knowledge.dto.kb.KbKnowledgeBaseVO;
import com.zjl.knowledge.service.KbKnowledgeBaseService;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 知识库列表 Tool
 */
@Component
@RequiredArgsConstructor
public class ListKnowledgeBasesTool implements McpTool {

    private final KbKnowledgeBaseService kbKnowledgeBaseService;

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("list_knowledge_bases")
                .description("查询当前用户可见的知识库列表，包含每个知识库的文档数量。"
                        + "适用场景：用户询问'有哪些知识库'、'列出我的知识库'等")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .properties(new LinkedHashMap<>())
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        KbKnowledgeBasePageRequest request = new KbKnowledgeBasePageRequest();
        request.setCurrent(1);
        request.setSize(50);

        IPage<KbKnowledgeBaseVO> pageResult = kbKnowledgeBaseService.pageQuery(request, user);

        List<Map<String, Object>> records = pageResult.getRecords().stream()
                .map(kb -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", kb.getId());
                    m.put("name", kb.getName());
                    m.put("embeddingModel", kb.getEmbeddingModel());
                    m.put("collectionName", kb.getCollectionName());
                    m.put("documentCount", kb.getDocumentCount());
                    m.put("createdAt", kb.getCreatedAt());
                    return m;
                })
                .toList();

        return ToolResult.success(records);
    }
}
