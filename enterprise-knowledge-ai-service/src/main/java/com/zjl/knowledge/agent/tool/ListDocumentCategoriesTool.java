package com.zjl.knowledge.agent.tool;

import com.zjl.knowledge.agent.mcp.McpTool;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.entity.KbCategory;
import com.zjl.knowledge.service.KbCategoryService;
import com.zjl.knowledge.web.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 文档分类列表 Tool（管理员上传文档前选 category_id 用）。
 */
@Component
@RequiredArgsConstructor
public class ListDocumentCategoriesTool implements McpTool {

    private final KbCategoryService kbCategoryService;

    @Override
    public boolean isAllowed(UserContext user) {
        return user != null && user.isAdmin();
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("list_document_categories")
                .description("查询知识库文档分类列表，上传文档前用于选择 category_id。仅管理员可用。")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .properties(new LinkedHashMap<>())
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        List<Map<String, Object>> categories = kbCategoryService.list().stream()
                .map(this::toMap)
                .toList();
        return ToolResult.success(categories);
    }

    private Map<String, Object> toMap(KbCategory category) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", category.getId());
        map.put("name", category.getCategoryName());
        map.put("parentId", category.getParentId());
        map.put("categoryType", category.getCategoryType());
        return map;
    }
}
