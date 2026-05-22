package com.zjl.knowledge.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjl.knowledge.agent.config.AgentProperties;
import com.zjl.knowledge.agent.mcp.McpTool;
import com.zjl.knowledge.agent.mcp.ToolDefinition;
import com.zjl.knowledge.agent.mcp.ToolResult;
import com.zjl.knowledge.web.UserContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 联网搜索 Tool — 调用博查 AI 搜索 API 检索互联网内容
 */
@Slf4j
@Component
public class WebSearchTool implements McpTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final AgentProperties properties;
    private final HttpClient httpClient;

    public WebSearchTool(AgentProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @Override
    public ToolDefinition getDefinition() {
        return ToolDefinition.builder()
                .name("web_search")
                .description("搜索互联网获取最新信息。当用户询问的内容超出知识库范围，或需要最新资讯时使用此工具。"
                        + "适用场景：用户询问'最新消息'、'最近发生了什么'、'查一下XX'等需要联网的问题")
                .inputSchema(ToolDefinition.JsonSchema.builder()
                        .required(List.of("query"))
                        .properties(new LinkedHashMap<>() {{
                            put("query", ToolDefinition.PropertyDef.builder()
                                    .type("string")
                                    .description("搜索关键词")
                                    .build());
                            put("count", ToolDefinition.PropertyDef.builder()
                                    .type("integer")
                                    .description("返回条数，默认8，最大20")
                                    .defaultValue(8)
                                    .build());
                        }})
                        .build())
                .build();
    }

    @Override
    public ToolResult execute(Map<String, Object> args, UserContext user) {
        String query = (String) args.get("query");
        if (query == null || query.isBlank()) {
            return ToolResult.failure("搜索关键词不能为空");
        }

        int count = getInt(args, "count", properties.getWebSearch().getCount());
        count = Math.min(count, 20);

        try {
            AgentProperties.WebSearch config = properties.getWebSearch();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("query", query);
            body.put("count", count);
            body.put("freshness", config.getFreshness());

            String json = MAPPER.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(config.getBaseUrl() + "/ai/search"))
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(30))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String responseBody = response.body();

            if (response.statusCode() != 200) {
                log.warn("博查 API 返回非 200: status={}, body={}", response.statusCode(), responseBody);
                return ToolResult.failure("联网搜索失败，API 返回状态码: " + response.statusCode());
            }

            return ToolResult.success(parseResults(responseBody));
        } catch (Exception e) {
            log.error("联网搜索异常", e);
            return ToolResult.failure("联网搜索异常: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> parseResults(String responseBody) throws Exception {
        Map<String, Object> root = MAPPER.readValue(responseBody, Map.class);
        Object data = root.get("data");
        if (data instanceof Map) {
            Object pages = ((Map<String, Object>) data).get("pages");
            if (pages instanceof List) {
                return (List<Map<String, Object>>) pages;
            }
        }
        return List.of();
    }

    private int getInt(Map<String, Object> args, String key, int defaultValue) {
        Object v = args.get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        return defaultValue;
    }
}
